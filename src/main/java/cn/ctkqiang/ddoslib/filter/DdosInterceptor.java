package cn.ctkqiang.ddoslib.filter;

import cn.ctkqiang.ddoslib.service.DdosProtectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * DDoS拦截器
 * 用于拦截和处理可能的DDoS攻击请求
 * 通过Spring的HandlerInterceptor机制实现请求预处理
 */
@Component
public class DdosInterceptor implements HandlerInterceptor {

    /**
     * DDoS防护服务实例
     * 用于检查IP是否被封禁以及获取重定向URL
     */
    private final DdosProtectionService ddosProtectionService;

    /**
     * 构造函数
     * 
     * @param ddosProtectionService DDoS防护服务的依赖注入
     */
    public DdosInterceptor(DdosProtectionService ddosProtectionService) {
        this.ddosProtectionService = ddosProtectionService;
    }

    /**
     * 请求预处理方法
     * 在Controller处理请求前进行拦截
     * 
     * @param request  HTTP请求对象，用于获取客户端IP地址
     * @param response HTTP响应对象，用于执行重定向操作
     * @param handler  请求处理器，包含请求将要被处理的相关信息
     * @return 如果请求允许继续处理返回true，否则返回false
     * @throws Exception 处理过程中可能发生的异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String requestUri = request.getRequestURI();

        if (ddosProtectionService.isBlocked(ip, userAgent, requestUri)) {
            response.sendRedirect(ddosProtectionService.getRedirectUrl());
            return false;
        }
        return true;
    }
}
