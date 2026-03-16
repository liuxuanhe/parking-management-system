package com.parking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parking.interceptor.AccessLogInterceptor;
import com.parking.interceptor.AccountRateLimitInterceptor;
import com.parking.interceptor.AuthenticationInterceptor;
import com.parking.interceptor.RateLimitInterceptor;
import com.parking.interceptor.RequestIdInterceptor;
import com.parking.service.AntiReplayService;
import com.parking.service.JwtTokenService;
import com.parking.service.SignatureService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web 配置类
 * 注册拦截器，配置路径匹配规则
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 不需要认证的路径列表 */
    private static final List<String> EXCLUDE_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/owner-login",
            "/api/v1/auth/refresh",
            "/api/v1/owners/register",
            "/api/v1/verification-code/**"
    );

    private final JwtTokenService jwtTokenService;
    private final SignatureService signatureService;
    private final AntiReplayService antiReplayService;
    private final ObjectMapper objectMapper;
    private final AccessLogInterceptor accessLogInterceptor;
    private final RedisTemplate<String, Object> redisTemplate;

    public WebConfig(JwtTokenService jwtTokenService,
                     SignatureService signatureService,
                     AntiReplayService antiReplayService,
                     ObjectMapper objectMapper,
                     AccessLogInterceptor accessLogInterceptor,
                     RedisTemplate<String, Object> redisTemplate) {
        this.jwtTokenService = jwtTokenService;
        this.signatureService = signatureService;
        this.antiReplayService = antiReplayService;
        this.objectMapper = objectMapper;
        this.accessLogInterceptor = accessLogInterceptor;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // RequestIdInterceptor 优先执行，为每个请求生成唯一 ID
        registry.addInterceptor(new RequestIdInterceptor())
                .addPathPatterns("/api/**")
                .order(0);

        // RateLimitInterceptor 在 RequestId 之后执行，限流保护
        registry.addInterceptor(new RateLimitInterceptor(redisTemplate, objectMapper))
                .addPathPatterns("/api/**")
                .order(1);

        // AuthenticationInterceptor 在限流之后执行
        registry.addInterceptor(new AuthenticationInterceptor(
                        jwtTokenService, signatureService, antiReplayService, objectMapper))
                .addPathPatterns("/api/**")
                .excludePathPatterns(EXCLUDE_PATHS)
                .order(2);

        // AccountRateLimitInterceptor 在认证之后执行，基于已认证的 userId 做账号级限流
        registry.addInterceptor(new AccountRateLimitInterceptor(redisTemplate, objectMapper))
                .addPathPatterns("/api/**")
                .order(3);

        // AccessLogInterceptor 最后执行，记录所有接口访问日志
        registry.addInterceptor(accessLogInterceptor)
                .addPathPatterns("/api/**")
                .order(4);
    }
}
