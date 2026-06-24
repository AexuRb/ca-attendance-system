package com.ca.attendance.auth;

import com.ca.attendance.common.ApiException;

public final class AuthContext {
    private static final ThreadLocal<AuthUser> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthUser user) {
        CURRENT.set(user);
    }

    public static AuthUser current() {
        AuthUser user = CURRENT.get();
        if (user == null) {
            throw ApiException.unauthorized("请先登录");
        }
        return user;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
