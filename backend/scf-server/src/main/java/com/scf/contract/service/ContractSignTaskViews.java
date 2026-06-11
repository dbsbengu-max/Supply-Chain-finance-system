package com.scf.contract.service;

import com.scf.contract.dto.ContractSignDtos.ContractSignTaskView;
import com.scf.contract.entity.TrContractSignTask;

final class ContractSignTaskViews {

    private ContractSignTaskViews() {
    }

    static ContractSignTaskView toView(TrContractSignTask task) {
        return new ContractSignTaskView(
                task.getId(),
                task.getDocumentId(),
                task.getProviderCode(),
                task.getExternalSignRef(),
                task.getTaskStatus(),
                task.getCallbackStatus(),
                task.getFailureReason(),
                task.getRetryCount(),
                task.getLastRetryAt(),
                task.getSignedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getPlatformTraceId(),
                task.getProviderRequestId(),
                task.getProviderTraceId());
    }
}
