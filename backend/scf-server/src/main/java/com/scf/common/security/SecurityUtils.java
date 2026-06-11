package com.scf.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserContext currentUser() {
        UserContext user = optionalCurrentUser();
        if (user == null) {
            throw new com.scf.common.exception.BusinessException("AUTH_401", "未登录", 401);
        }
        return user;
    }

    public static UserContext optionalCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserContext userContext) {
            return userContext;
        }
        return null;
    }

    public static String currentUserId() {
        UserContext user = optionalCurrentUser();
        return user == null ? null : user.userId();
    }
}
