package com.scf.iam.dto;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String userId,
        String loginName,
        String userName,
        List<IdentityView> identities,
        String currentIdentityId
) {
}
