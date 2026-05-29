package com.scf.iam.service;

import com.scf.common.security.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class PermissionService {

    private final JdbcTemplate jdbcTemplate;

    public PermissionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasPermission(UserContext user, String permissionCode) {
        if (user == null || user.roleId() == null || user.roleId().isBlank()) {
            return false;
        }
        return loadPermissions(user).contains(permissionCode);
    }

    public Set<String> loadPermissions(UserContext user) {
        if (user == null || user.roleId() == null || user.roleId().isBlank()) {
            return Set.of();
        }
        return new HashSet<>(jdbcTemplate.queryForList("""
                SELECT p.permission_code
                FROM scf.sys_role_permission rp
                JOIN scf.sys_permission p ON p.id = rp.permission_id
                WHERE rp.role_id = ?
                  AND p.status = 'ENABLED'
                  AND (p.operator_id IS NULL OR p.operator_id = ?)
                """, String.class, user.roleId(), user.operatorId()));
    }
}
