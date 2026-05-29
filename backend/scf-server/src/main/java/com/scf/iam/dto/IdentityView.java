package com.scf.iam.dto;

public record IdentityView(
        String identityId,
        String operatorId,
        String projectId,
        String enterpriseId,
        String roleId,
        String roleCode,
        String roleName,
        boolean isDefault
) {
}
