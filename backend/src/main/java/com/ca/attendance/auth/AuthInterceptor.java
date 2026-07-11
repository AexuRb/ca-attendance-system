package com.ca.attendance.auth;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.user.UserRepository;
import com.ca.attendance.user.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final TokenService tokenService;
    private final UserRepository users;

    public AuthInterceptor(TokenService tokenService, UserRepository users) {
        this.tokenService = tokenService;
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/api/public/") || path.startsWith("/api/setup/") || path.startsWith("/api/desktop/")
                || path.equals("/api/auth/login") || path.equals("/api/health")) {
            return true;
        }
        String token = extractToken(request);
        AuthUser issued = tokenService.require(token);
        UserSummary current = users.findSummaryById(issued.id()).orElseGet(() -> {
            tokenService.revoke(token);
            throw ApiException.unauthorized("账号不存在，请重新登录");
        });
        if (!"ACTIVE".equals(current.status())) {
            tokenService.revoke(token);
            throw ApiException.forbidden("账号已停用，请联系管理员");
        }
        if (current.mustChangePassword() && !passwordChangeAllowed(path)) {
            throw ApiException.forbidden("请先修改初始密码");
        }
        AuthContext.set(new AuthUser(
                current.id(),
                current.studentNo(),
                current.name(),
                current.role(),
                issued.expiresAt()
        ));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        String token = request.getHeader("X-Auth-Token");
        return token == null ? "" : token.trim();
    }

    private boolean passwordChangeAllowed(String path) {
        return path.equals("/api/auth/me")
                || path.equals("/api/auth/change-password")
                || path.equals("/api/auth/logout")
                || path.equals("/api/health");
    }
}
