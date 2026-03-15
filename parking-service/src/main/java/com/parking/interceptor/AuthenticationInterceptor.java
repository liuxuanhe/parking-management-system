package com.parking.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parking.common.ApiResponse;
import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.common.RequestContext;
import com.parking.service.AntiReplayService;
import com.parking.service.JwtTokenService;
import com.parking.service.SignatureService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 认证鉴权拦截器
 * 负责验证 JWT Token、签名、timestamp、nonce，并记录访问日志
 * Validates: Requirements 19.4, 19.5, 18.3, 18.4
 */
@Slf4j
public class AuthenticationInterceptor implements HandlerInterceptor {

    /** 请求头名称常量 */
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_NONCE = "X-Nonce";
    private static final String HEADER_SIGNATURE = "X-Signature";
    private static final String BEARER_PREFIX = "Bearer ";

    /** 请求属性键，用于在请求中传递解析后的用户信息 */
    public static final String ATTR_USER_ID = "auth_userId";
    public static final String ATTR_USER_ROLE = "auth_userRole";
    public static final String ATTR_COMMUNITY_ID = "auth_communityId";
    public static final String ATTR_HOUSE_NO = "auth_houseNo";
    public static final String ATTR_ACCESS_START_TIME = "auth_accessStartTime";

    private final JwtTokenService jwtTokenService;
    private final SignatureService signatureService;
    private final AntiReplayService antiReplayService;
    private final ObjectMapper objectMapper;

    public AuthenticationInterceptor(JwtTokenService jwtTokenService,
                                     SignatureService signatureService,
                                     AntiReplayService antiReplayService,
                                     ObjectMapper objectMapper) {
        this.jwtTokenService = jwtTokenService;
        this.signatureService = signatureService;
        this.antiReplayService = antiReplayService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 记录请求开始时间，用于后续计算响应时间
        request.setAttribute(ATTR_ACCESS_START_TIME, System.currentTimeMillis());

        try {
            // 1. 验证 JWT Token
            String token = extractToken(request);
            Claims claims = jwtTokenService.validateToken(token);

            // 将用户信息存入请求属性，供后续使用
            Long userId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);
            Object communityIdObj = claims.get("communityId");
            Long communityId = communityIdObj != null ? Long.valueOf(communityIdObj.toString()) : null;
            String houseNo = claims.get("houseNo", String.class);

            request.setAttribute(ATTR_USER_ID, userId);
            request.setAttribute(ATTR_USER_ROLE, role);
            request.setAttribute(ATTR_COMMUNITY_ID, communityId);
            request.setAttribute(ATTR_HOUSE_NO, houseNo);

            // 同时设置 Controller 层使用的短键名，保持向后兼容
            request.setAttribute("userId", userId);
            request.setAttribute("userRole", role);
            request.setAttribute("communityId", communityId);
            request.setAttribute("houseNo", houseNo);

            // 2. 验证防重放（timestamp + nonce）
            String timestamp = request.getHeader(HEADER_TIMESTAMP);
            String nonce = request.getHeader(HEADER_NONCE);
            antiReplayService.validate(timestamp, nonce);

            // 3. 验证签名
            String signature = request.getHeader(HEADER_SIGNATURE);
            String requestBody = extractRequestBody(request);
            signatureService.verifySignature(timestamp, nonce, requestBody, signature);

            log.info("认证通过, userId: {}, role: {}, communityId: {}, path: {} {}",
                    userId, role, communityId, request.getMethod(), request.getRequestURI());

            return true;
        } catch (BusinessException e) {
            log.warn("认证失败, path: {} {}, error: {}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());
            writeErrorResponse(response, e.getCode(), e.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 记录访问日志（预留接口，后续在审计日志模块中完善）
        try {
            Long startTime = (Long) request.getAttribute(ATTR_ACCESS_START_TIME);
            long responseTime = startTime != null ? System.currentTimeMillis() - startTime : 0;

            Long userId = (Long) request.getAttribute(ATTR_USER_ID);
            String role = (String) request.getAttribute(ATTR_USER_ROLE);

            log.info("访问日志 - userId: {}, role: {}, ip: {}, method: {}, path: {}, status: {}, responseTime: {}ms",
                    userId, role, getClientIp(request),
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), responseTime);
        } catch (Exception e) {
            log.error("记录访问日志异常", e);
        }
    }

    /**
     * 从 Authorization 请求头中提取 Bearer Token
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HEADER_AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return token;
    }

    /**
     * 提取请求体内容
     * 支持 ContentCachingRequestWrapper 以便多次读取请求体
     */
    private String extractRequestBody(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            byte[] content = wrapper.getContentAsByteArray();
            if (content.length > 0) {
                return new String(content, StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    /**
     * 获取客户端真实 IP 地址
     * 优先从代理头中获取
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // 多级代理时取第一个 IP
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    /**
     * 向客户端写入错误响应（JSON 格式）
     */
    private void writeErrorResponse(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> apiResponse = ApiResponse.error(code, message, RequestContext.getRequestId());
        String json = objectMapper.writeValueAsString(apiResponse);
        response.getWriter().write(json);
        response.getWriter().flush();
    }
}
