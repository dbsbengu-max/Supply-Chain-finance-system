package com.scf.common.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FieldMaskService {

    private final JdbcTemplate jdbcTemplate;

    public FieldMaskService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String apply(String objectType, String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        UserContext user = SecurityUtils.currentUser();
        String policy = policy(user.roleId(), objectType, fieldName);
        if ("HIDE".equals(policy)) {
            return null;
        }
        if ("MASK".equals(policy)) {
            return mask(value);
        }
        return value;
    }

    private String policy(String roleId, String objectType, String fieldName) {
        return jdbcTemplate.query("""
                SELECT field_policy
                FROM scf.sys_field_permission
                WHERE role_id = ?
                  AND object_type = ?
                  AND field_name = ?
                """, rs -> rs.next() ? rs.getString("field_policy") : "VISIBLE", roleId, objectType, fieldName);
    }

    private String mask(String value) {
        int length = value.length();
        if (length <= 4) {
            return "****";
        }
        if (length <= 8) {
            return value.substring(0, 2) + "****" + value.substring(length - 2);
        }
        return value.substring(0, 4) + "****" + value.substring(length - 4);
    }
}
