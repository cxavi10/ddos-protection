package cn.ctkqiang.ddoslib.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DDoS防护数据库服务类
 * 用于管理被封禁IP的存储、查询和日志记录
 * 使用SQLite作为本地数据存储
 */
@Service
public class DdosDatabase {
    // 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(DdosDatabase.class);
    // SQLite数据库文件路径
    private static final String DB_PATH = "ddos_protection.db";
    // 日志文件目录
    private static final String LOG_DIR = "log";

    /**
     * 构造函数
     * 初始化数据库，创建必要的表结构
     */
    public DdosDatabase() {
        initializeDatabase();
    }

    /**
     * 初始化数据库
     * 创建blocked_ips表，用于存储被封禁的IP信息
     * 包含IP地址、位置、封禁时间、请求次数等信息
     */
    private void initializeDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS blocked_ips ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ip TEXT NOT NULL UNIQUE,"
                + "location TEXT,"
                + "country_code TEXT,"
                + "block_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + "unblock_time TIMESTAMP,"
                + "request_count INTEGER DEFAULT 0,"
                + "last_request_rate DOUBLE DEFAULT 0.0,"
                + "last_request_time TIMESTAMP,"
                + "user_agent TEXT,"
                + "request_uri TEXT,"
                + "is_active BOOLEAN DEFAULT 1,"
                + "block_reason TEXT"
                + ")";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("Database initialized successfully");
        } catch (SQLException e) {
            logger.error("Database initialization failed", e);
        }
    }

    /**
     * 获取数据库连接
     * 
     * @return SQLite数据库连接
     * @throws SQLException 数据库连接异常
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
    }

    /**
     * 保存被封禁的IP信息
     * 
     * @param ip           IP地址
     * @param location     地理位置
     * @param requestCount 请求次数
     * @param requestRate  请求频率
     * @param userAgent    用户代理
     * @param requestUri   请求URI
     */
    public void saveBlockedIP(String ip, String location, int requestCount,
            double requestRate, String userAgent, String requestUri) {
        String sql = "INSERT INTO blocked_ips (ip, location, request_count, "
                + "last_request_rate, user_agent, request_uri) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ip);
            pstmt.setString(2, location);
            pstmt.setInt(3, requestCount);
            pstmt.setDouble(4, requestRate);
            pstmt.setString(5, userAgent);
            pstmt.setString(6, requestUri);
            pstmt.executeUpdate();

            logToFile(String.format("Blocked IP saved: %s, Location: %s", ip, location));
        } catch (SQLException e) {
            logger.error("Error saving blocked IP", e);
        }
    }

    /**
     * 获取所有被封禁的IP列表
     * 按封禁时间降序排序
     * 
     * @return 被封禁IP列表
     */
    public List<BlockedIP> getBlockedIPs() {
        List<BlockedIP> blockedIPs = new ArrayList<>();
        String sql = "SELECT * FROM blocked_ips ORDER BY block_time DESC";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                BlockedIP ip = new BlockedIP(
                        rs.getString("ip"),
                        rs.getString("location"),
                        rs.getTimestamp("block_time"),
                        rs.getInt("request_count"),
                        rs.getDouble("last_request_rate"),
                        rs.getString("user_agent"),
                        rs.getString("request_uri"));
                blockedIPs.add(ip);
            }
        } catch (SQLException e) {
            logger.error("Error retrieving blocked IPs", e);
        }
        return blockedIPs;
    }

    /**
     * 更新被封禁IP的信息
     * 
     * @param ip              要更新的IP地址
     * @param newRequestCount 新的请求次数
     * @param newRequestRate  新的请求频率
     */
    public void updateBlockedIP(String ip, int newRequestCount, double newRequestRate) {
        String sql = "UPDATE blocked_ips "
                + "SET request_count = ?, last_request_rate = ? "
                + "WHERE ip = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, newRequestCount);
            pstmt.setDouble(2, newRequestRate);
            pstmt.setString(3, ip);
            pstmt.executeUpdate();

            logToFile(String.format("Updated IP: %s, Count: %d, Rate: %.2f",
                    ip, newRequestCount, newRequestRate));
        } catch (SQLException e) {
            logger.error("Error updating blocked IP", e);
        }
    }

    /**
     * 删除被封禁的IP记录
     * 
     * @param ip 要删除的IP地址
     */
    public void deleteBlockedIP(String ip) {
        String sql = "DELETE FROM blocked_ips WHERE ip = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ip);
            pstmt.executeUpdate();

            logToFile(String.format("Deleted blocked IP: %s", ip));
        } catch (SQLException e) {
            logger.error("Error deleting blocked IP", e);
        }
    }

    /**
     * 记录操作日志到文件
     * 日志文件按日期命名，格式：log/yyyy-MM-dd.log
     * 
     * @param message 日志消息
     */
    private void logToFile(String message) {
        File logDir = new File(LOG_DIR);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        String filename = String.format("%s/%s.log",
                LOG_DIR,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        try (FileWriter fw = new FileWriter(filename, true);
                BufferedWriter bw = new BufferedWriter(fw);
                PrintWriter out = new PrintWriter(bw)) {

            out.println(String.format("[%s] %s",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    message));
        } catch (IOException e) {
            logger.error("Error writing to log file", e);
        }
    }

    /**
     * 下载指定日期的日志文件
     * 
     * @param date      日期
     * @param targetUrl 目标URL
     */
    public void downloadLog(String date, String targetUrl) {
        String sourceFile = String.format("%s/%s.log", LOG_DIR, date);
        try {
            URL url = new URL(targetUrl);
            try (ReadableByteChannel rbc = Channels.newChannel(url.openStream());
                    FileOutputStream fos = new FileOutputStream(sourceFile)) {
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                logger.info("Log file downloaded successfully");
            }
        } catch (IOException e) {
            logger.error("Error downloading log file", e);
        }
    }

    /**
     * 被封禁IP信息的数据模型类
     * 用于封装IP相关的所有属性
     */
    public static class BlockedIP {
        // IP地址
        private String ip;
        // 地理位置
        private String location;
        // 封禁时间
        private Timestamp blockTime;
        // 请求次数
        private int requestCount;
        // 最后请求频率
        private double lastRequestRate;
        // 用户代理
        private String userAgent;
        // 请求URI
        private String requestUri;

        /**
         * 构造函数
         * 
         * @param ip              IP地址
         * @param location        地理位置
         * @param blockTime       封禁时间
         * @param requestCount    请求次数
         * @param lastRequestRate 最后请求频率
         * @param userAgent       用户代理
         * @param requestUri      请求URI
         */
        public BlockedIP(String ip, String location, Timestamp blockTime,
                int requestCount, double lastRequestRate,
                String userAgent, String requestUri) {
            this.ip = ip;
            this.location = location;
            this.blockTime = blockTime;
            this.requestCount = requestCount;
            this.lastRequestRate = lastRequestRate;
            this.userAgent = userAgent;
            this.requestUri = requestUri;
        }

        // Getters and setters with Chinese comments
        /**
         * 获取IP地址
         * 
         * @return IP地址
         */
        public String getIp() {
            return ip;
        }

        /**
         * 设置IP地址
         * 
         * @param ip IP地址
         */
        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public Timestamp getBlockTime() {
            return blockTime;
        }

        public void setBlockTime(Timestamp blockTime) {
            this.blockTime = blockTime;
        }

        public int getRequestCount() {
            return requestCount;
        }

        public void setRequestCount(int requestCount) {
            this.requestCount = requestCount;
        }

        public double getLastRequestRate() {
            return lastRequestRate;
        }

        public void setLastRequestRate(double lastRequestRate) {
            this.lastRequestRate = lastRequestRate;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public String getRequestUri() {
            return requestUri;
        }

        public void setRequestUri(String requestUri) {
            this.requestUri = requestUri;
        }

        @Override
        public String toString() {
            return "BlockedIP{" +
                    "ip='" + ip + '\'' +
                    ", location='" + location + '\'' +
                    ", blockTime=" + blockTime +
                    ", requestCount=" + requestCount +
                    ", lastRequestRate=" + lastRequestRate +
                    ", userAgent='" + userAgent + '\'' +
                    ", requestUri='" + requestUri + '\'' +
                    '}';
        }
    }
}
