package com.scf.common.dto;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        String requestId,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data, String requestId) {
        return new ApiResponse<>(true, "OK", "success", data, requestId, Instant.now());
    }

    public static <T> ApiResponse<T> fail(String code, String message, String requestId) {
        return new ApiResponse<>(false, code, message, null, requestId, Instant.now());
    }
}
