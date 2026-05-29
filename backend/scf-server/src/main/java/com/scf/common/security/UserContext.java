package com.scf.common.security;

public record UserContext(
        String userId,
        String loginName,
        String operatorId,
        String projectId,
        String enterpriseId,
        String roleId,
        String identityId
) {
}
