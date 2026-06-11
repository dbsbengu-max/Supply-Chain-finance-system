package com.scf.contract.provider.model;

import java.time.Instant;

public record SignStatusResult(
        String externalSignRef,
        String status,
        Instant signedAt,
        String failureReason) {
}
