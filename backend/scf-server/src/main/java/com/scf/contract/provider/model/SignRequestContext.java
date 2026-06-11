package com.scf.contract.provider.model;

import java.util.List;

public record SignRequestContext(
        String taskId,
        String documentId,
        String fileId,
        String documentNo,
        String businessType,
        String businessId,
        List<SignerRef> signers,
        boolean simulateFailure) {

    public record SignerRef(
            String enterpriseId,
            String signerName,
            String signerRole) {
    }
}
