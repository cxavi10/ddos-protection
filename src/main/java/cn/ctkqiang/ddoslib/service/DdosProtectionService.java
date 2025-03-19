package cn.ctkqiang.ddoslib.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cn.ctkqiang.ddoslib.service.DdosDatabase.BlockedIP;

import org.springframework.scheduling.annotation.Scheduled;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

/**
 * DDoS防护服务类
 * 
 * 本服务提供以下DDoS攻击防护机制：
 * 1. 请求频率限制：限制单个IP在指定时间窗口内的请求数量
 * 2. 可疑行为检测：监控请求模式，识别异常行为
 * 3. IP封禁：对超出限制或表现可疑的IP进行临时封禁
 * 4. 数据持久化：将被封禁的IP信息保存到数据库
 * 5. 自动清理：定期清理过期的封禁记录和请求记录
 * 
 * 配置参数说明：
 * - REQUEST_LIMIT: 单个时间窗口内允许的最大请求数（默认：100次）
 * - TIME_WINDOW: 时间窗口大小（默认：60秒）
 * - BLOCK_DURATION: IP封禁持续时间（默认：300秒）
 * - CLEANUP_INTERVAL: 清理任务执行间隔（默认：3600秒）
 * 
 * 使用方法：
 * 1. 在Web安全配置中注入此服务
 * 2. 使用isBlocked()方法检查传入请求
 * 3. 配置被封禁请求的重定向URL
 * 4. 通过getBlockedIPs()监控被封禁的IP
 */
@Service
public class DdosProtectionService {
    // 每个时间窗口内允许的最大请求数
    private static final int REQUEST_LIMIT = 100;
    // 时间窗口大小：1分钟（毫秒）
    private static final long TIME_WINDOW = 60_000;
    // IP封禁持续时间：5分钟（毫秒）
    private static final long BLOCK_DURATION = 300_000;
    // 清理任务执行间隔：1小时（毫秒）
    private static final long CLEANUP_INTERVAL = 3600_000;

    // 重定向URL列表，用于将可疑请求重定向到其他位置
    private final List<String> redirectUrls;
    private final Random random = new Random();

    // 存储IP请求信息的并发哈希表
    private final ConcurrentHashMap<String, RequestInfo> requestMap = new ConcurrentHashMap<>();
    // 存储被封禁IP及其封禁时间的并发哈希表
    private final ConcurrentHashMap<String, Long> blockedIPs = new ConcurrentHashMap<>();

    // Add new field for database service
    private final DdosDatabase ddosDatabase;

    /**
     * 构造函数：初始化重定向URL配置和数据库服务
     */
    /**
     * DDoS防护服务构造函数
     * 
     * 初始化服务配置和数据库连接。重定向URL用于将被封禁的请求重定向到指定页面。
     * 
     * 配置方法：
     * 在application.properties中添加以下配置：
     * ddos.protection.redirect.urls=/blocked,/error,/maintenance
     * 
     * 如果未配置重定向URL，将默认使用"/"作为重定向地址
     * 
     * @param redirectUrlConfig 重定向URL配置，多个URL用逗号分隔
     * @param ddosDatabase      数据库服务实例，用于持久化存储封禁记录
     */
    public DdosProtectionService(
            @Value("${ddos.protection.redirect.urls:}") String redirectUrlConfig,
            DdosDatabase ddosDatabase) {
        if (redirectUrlConfig == null || redirectUrlConfig.trim().isEmpty()) {
            this.redirectUrls = Arrays.asList("/");
        } else {
            this.redirectUrls = Arrays.asList(redirectUrlConfig.split(","));
        }
        this.ddosDatabase = ddosDatabase;
    }

    /**
     * 检查IP是否被封禁或是否需要被封禁
     */
    /**
     * 检查IP是否需要被封禁
     * 
     * 实现以下检查逻辑：
     * 1. 检查IP是否已被封禁
     * 2. 如果已封禁，检查是否过期
     * 3. 统计IP的请求频率
     * 4. 根据请求频率和次数判断是否需要封禁
     * 5. 将封禁信息保存到数据库
     * 
     * 触发封禁的条件：
     * - 请求次数超过REQUEST_LIMIT
     * - 请求频率超过SUSPICIOUS_RATE（每秒10次）
     * 
     * @param ip         请求的IP地址
     * @param userAgent  用户代理字符串
     * @param requestUri 请求的URI
     * @return 如果IP被封禁返回true，否则返回false
     */
    public boolean isBlocked(String ip, String userAgent, String requestUri) {
        long currentTime = System.currentTimeMillis();

        if (blockedIPs.containsKey(ip)) {
            long blockTime = blockedIPs.get(ip);
            if (currentTime - blockTime > BLOCK_DURATION) {
                blockedIPs.remove(ip);
                requestMap.remove(ip);
                ddosDatabase.deleteBlockedIP(ip);
            } else {
                return true;
            }
        }

        RequestInfo info = requestMap.computeIfAbsent(ip,
                k -> new RequestInfo(currentTime));

        synchronized (info) {
            if (currentTime - info.timestamp > TIME_WINDOW) {
                info.timestamp = currentTime;
                info.requestCount = 1;
                info.calculateRate(currentTime);
            } else {
                info.requestCount++;
                info.calculateRate(currentTime);

                if (info.requestCount > REQUEST_LIMIT || info.isRateSuspicious()) {
                    blockedIPs.put(ip, currentTime);
                    // Save to database when IP is blocked
                    ddosDatabase.saveBlockedIP(
                            ip,
                            getIpLocation(ip), // You'll need to implement this method
                            info.requestCount,
                            info.requestRate,
                            userAgent,
                            requestUri);
                    return true;
                }
            }

            // Update database with latest request info
            ddosDatabase.updateBlockedIP(ip, info.requestCount, info.requestRate);
        }

        return false;
    }

    /**
     * IP地理位置查询
     * 
     * 获取IP地址的地理位置信息，可以使用第三方服务实现：
     * - MaxMind GeoIP2
     * - IP2Location
     * - 其他IP地理位置数据库
     * 
     * 建议实现：
     * 1. 使用本地IP地理位置数据库
     * 2. 实现缓存机制避免重复查询
     * 3. 定期更新IP地理位置数据
     * 
     * @param ip 需要查询的IP地址
     * @return IP的地理位置信息
     */
    private String getIpLocation(String ip) {
        try {
            final String location = GeoLocationService.getIpLocation(ip);
            return location != null ? location : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 自动清理任务
     * 
     * 定期执行以下清理操作：
     * 1. 清理过期的IP封禁记录
     * 2. 从数据库中删除过期记录
     * 3. 清理过期的请求统计数据
     * 
     * 执行频率：
     * - 默认每小时执行一次（CLEANUP_INTERVAL = 3600000）
     * - 可通过修改CLEANUP_INTERVAL调整执行频率
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL)
    public void cleanup() {
        long currentTime = System.currentTimeMillis();

        // Cleanup expired blocked IPs and remove from database
        blockedIPs.entrySet().removeIf(entry -> {
            boolean expired = currentTime - entry.getValue() > BLOCK_DURATION;
            if (expired) {
                ddosDatabase.deleteBlockedIP(entry.getKey());
            }
            return expired;
        });

        // Cleanup old request records
        requestMap.entrySet().removeIf(entry -> currentTime - entry.getValue().timestamp > TIME_WINDOW);
    }

    /**
     * 获取所有被封禁的IP列表
     */
    /**
     * 获取被封禁IP列表
     * 
     * 从数据库中获取所有当前被封禁的IP信息，包括：
     * - IP地址
     * - 地理位置
     * - 封禁时间
     * - 请求统计
     * - 用户代理
     * - 请求URI
     * 
     * @return 被封禁IP信息列表
     */
    public List<BlockedIP> getBlockedIPs() {
        return ddosDatabase.getBlockedIPs();
    }

    /**
     * 获取重定向URL
     * 
     * 从配置的URL列表中随机选择一个URL，用于：
     * 1. 分散被封禁请求的流量
     * 2. 防止单个错误页面被大量请求
     * 
     * @return 随机选择的重定向URL
     */
    public String getRedirectUrl() {
        return redirectUrls.get(random.nextInt(redirectUrls.size()));
    }

    /**
     * 请求信息记录类
     * 
     * 用于记录和计算单个IP的请求统计信息：
     * - 记录请求时间戳
     * - 统计请求次数
     * - 计算请求频率
     * - 判断请求行为是否可疑
     * 
     * 可疑行为判断标准：
     * - 请求频率超过每秒10次
     * - 可通过修改SUSPICIOUS_RATE调整阈值
     */
    private static class RequestInfo {
        // 最后一次请求的时间戳
        long timestamp;
        // 在当前时间窗口内的请求计数
        int requestCount;
        // 当前请求频率（每秒请求数）
        double requestRate;
        // 可疑请求频率阈值：每秒10次请求
        static final double SUSPICIOUS_RATE = 10.0;

        /**
         * 构造函数
         * 
         * @param timestamp 初始时间戳
         */
        RequestInfo(long timestamp) {
            this.timestamp = timestamp;
            this.requestCount = 1;
            this.requestRate = 0.0;
        }

        /**
         * 计算当前请求频率
         * 
         * @param currentTime 当前时间
         */
        void calculateRate(long currentTime) {
            double timeFrame = (currentTime - timestamp) / 1000.0;
            if (timeFrame > 0) {
                this.requestRate = requestCount / timeFrame;
            }
        }

        /**
         * 检查请求频率是否可疑
         * 
         * @return 如果请求频率超过阈值返回true，否则返回false
         */
        boolean isRateSuspicious() {
            return requestRate > SUSPICIOUS_RATE;
        }
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public boolean equals(Object arg0) {
        return super.equals(arg0);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
