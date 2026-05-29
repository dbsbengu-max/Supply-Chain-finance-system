package com.scf.common.security;

import com.scf.common.exception.BusinessException;
import com.scf.iam.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class TenantContext {

    private final PermissionService permissionService;

    public TenantContext(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public String requireOperatorId() {
        UserContext user = SecurityUtils.currentUser();
        String header = currentRequestHeader("X-Operator-Id");
        if (user.operatorId() != null && !user.operatorId().isBlank()) {
            assertHeaderMatchesContext("X-Operator-Id", header, user.operatorId());
            return user.operatorId();
        }
        if (header == null || header.isBlank()) {
            throw new BusinessException("VALID_400", "缺少 X-Operator-Id", 400);
        }
        return header;
    }

    public String projectId() {
        UserContext user = SecurityUtils.currentUser();
        String header = currentRequestHeader("X-Project-Id");
        if (user.projectId() != null && !user.projectId().isBlank()) {
            if (header == null || header.isBlank()) {
                return null;
            }
            assertHeaderMatchesContext("X-Project-Id", header, user.projectId());
            return user.projectId();
        }
        return header;
    }

    /** 业务写操作与按项目隔离的读操作必须调用，缺少项目上下文时返回 VALID_400。 */
    public String requireProjectId() {
        String projectId = projectId();
        if (projectId == null || projectId.isBlank()) {
            throw new BusinessException("VALID_400", "缺少 X-Project-Id", 400);
        }
        return projectId;
    }

    public void requirePermission(String permissionCode) {
        UserContext user = SecurityUtils.currentUser();
        if (!permissionService.hasPermission(user, permissionCode)) {
            throw new BusinessException("AUTH_403", "无权限: " + permissionCode, 403);
        }
    }

    private String currentRequestHeader(String name) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        return request.getHeader(name);
    }

    private void assertHeaderMatchesContext(String headerName, String headerValue, String contextValue) {
        if (headerValue != null && !headerValue.isBlank() && !headerValue.equals(contextValue)) {
            throw new BusinessException("AUTH_403", headerName + " 与当前登录身份不一致", 403);
        }
    }
}
