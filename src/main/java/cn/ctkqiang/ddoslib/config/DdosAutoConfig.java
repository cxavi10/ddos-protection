package cn.ctkqiang.ddoslib.config;

import cn.ctkqiang.ddoslib.filter.DdosInterceptor;
import cn.ctkqiang.ddoslib.service.DdosProtectionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * DDoS防护自动配置类
 * 用于自动配置DDoS防护相关的Bean和拦截器
 * 当配置属性ddos.protection.enabled为true时激活（默认激活）
 */
@Configuration
@ConditionalOnProperty(prefix = "ddos.protection", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DdosAutoConfig implements WebMvcConfigurer {

    /**
     * 创建DDoS防护服务Bean
     * 当容器中不存在DdosProtectionService实例时创建
     * 
     * @return DDoS防护服务实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DdosProtectionService ddosProtectionService() {
        return new DdosProtectionService("", null);
    }

    /**
     * 创建DDoS拦截器Bean
     * 当容器中不存在DdosInterceptor实例时创建
     * 
     * @param ddosProtectionService DDoS防护服务依赖
     * @return DDoS拦截器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DdosInterceptor ddosInterceptor(DdosProtectionService ddosProtectionService) {
        return new DdosInterceptor(ddosProtectionService);
    }

    /**
     * 配置拦截器
     * 将DDoS拦截器添加到Spring MVC的拦截器链中
     * 
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ddosInterceptor(ddosProtectionService()));
    }
}
