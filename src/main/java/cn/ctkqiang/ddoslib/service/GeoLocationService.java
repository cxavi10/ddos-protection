package cn.ctkqiang.ddoslib.service;

import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IP地理位置查询服务
 * 
 * 本服务使用ip-api.com的免费API进行IP地理位置查询
 * 主要功能：
 * 1. 查询IP地址的地理位置信息
 * 2. 返回国家和城市信息
 * 3. 处理各种异常情况
 * 
 * 使用限制：
 * - 免费版API每分钟限制45次请求
 * - 仅支持HTTP协议
 * - 不需要API密钥
 */
public class GeoLocationService {
    // 日志记录器，用于记录查询过程中的各种状态和错误
    private static final Logger logger = LoggerFactory.getLogger(GeoLocationService.class);

    // IP-API的接口地址
    private static final String API_URL = "http://ip-api.com/json/";

    // REST请求客户端，用于发送HTTP请求
    private static final RestTemplate restTemplate = new RestTemplate();

    /**
     * 查询IP地址的地理位置信息
     * 
     * 查询流程：
     * 1. 验证IP地址是否有效
     * 2. 调用ip-api.com的API获取位置信息
     * 3. 解析JSON响应数据
     * 4. 提取国家和城市信息
     * 
     * 异常处理：
     * - IP地址为空：返回"Unknown"
     * - API调用失败：返回"Unknown"
     * - JSON解析错误：返回"Unknown"
     * 
     * @param ip 要查询的IP地址
     * @return 格式化的地理位置信息（格式：国家, 城市），如查询失败则返回"Unknown"
     */
    public static String getIpLocation(String ip) {
        // 验证IP地址是否为空
        if (ip == null || ip.trim().isEmpty()) {
            logger.error("IP地址不能为空");
            return "Unknown";
        }

        try {
            // 调用API获取地理位置信息
            String response = restTemplate.getForObject(API_URL + ip.trim(), String.class);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(response);

            // 解析API响应数据
            if ("success".equals(json.get("status").asText())) {
                String country = json.get("country").asText();
                String city = json.get("city").asText();
                String location = String.format("%s, %s", country, city);
                logger.info("IP: {} 的地理位置是: {}", ip, location);
                return location;
            } else {
                logger.warn("无法获取IP: {} 的地理位置信息", ip);
                return "Unknown";
            }
        } catch (RestClientException e) {
            // 处理API调用异常
            logger.error("调用地理位置API时发生错误: {}", e.getMessage());
            return "Unknown";
        } catch (Exception e) {
            // 处理其他未预期的异常
            logger.error("处理地理位置信息时发生错误: {}", e.getMessage());
            return "Unknown";
        }
    }
}
