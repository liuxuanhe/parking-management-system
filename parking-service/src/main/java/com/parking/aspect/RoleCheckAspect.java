package com.parking.aspect;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.common.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

/**
 * 角色校验切面
 * 拦截标注了 @RequireRole 的方法，从请求属性中读取 auth_userRole 并校验
 * 校验失败抛出 BusinessException(ErrorCode.PARKING_12001)
 * Validates: Requirements 12.3, 14.1, 20.4
 */
@Slf4j
@Aspect
@Component
public class RoleCheckAspect {

    @Around("@annotation(requireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint, RequireRole requireRole) throws Throwable {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        HttpServletRequest request = attrs.getRequest();
        String userRole = (String) request.getAttribute("auth_userRole");
        if (userRole == null) {
            userRole = (String) request.getAttribute("userRole");
        }

        if (userRole == null) {
            log.warn("角色校验失败：未获取到用户角色, path={}", request.getRequestURI());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String[] allowedRoles = requireRole.value();
        boolean allowed = Arrays.asList(allowedRoles).contains(userRole);

        if (!allowed) {
            log.warn("角色校验失败：用户角色 {} 不在允许列表 {} 中, path={}",
                    userRole, Arrays.toString(allowedRoles), request.getRequestURI());
            throw new BusinessException(ErrorCode.PARKING_12001);
        }

        return joinPoint.proceed();
    }
}
