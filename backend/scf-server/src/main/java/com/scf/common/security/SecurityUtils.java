package com.scf.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserContext currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserContext userContext) {
            return userContext;
        }
        throw new com.scf.common.exception.BusinessException("AUTH_401", "未登录", 401);
    }

    public static String currentUserId() {
        return currentUser().userId();
    }
}
