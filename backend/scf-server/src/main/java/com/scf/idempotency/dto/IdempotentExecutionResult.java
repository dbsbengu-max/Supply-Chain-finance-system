package com.scf.idempotency.dto;

public record IdempotentExecutionResult<T>(T value, boolean replay) {
}
