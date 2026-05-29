package com.scf.common.security;

import com.scf.common.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class SecondaryAuthVerifier {

    public static final String MOCK_APPROVED_TOKEN = "MOCK-APPROVED";

    public void requireValid(String secondaryAuthToken) {
        if (secondaryAuthToken == null || secondaryAuthToken.isBlank()) {
            throw new BusinessException("VALID_400", "缺少 X-Secondary-Auth-Token", 400);
        }
        if (!MOCK_APPROVED_TOKEN.equals(secondaryAuthToken.trim())) {
            throw new BusinessException("AUTH_403", "二次确认失败", 403);
        }
    }
}
