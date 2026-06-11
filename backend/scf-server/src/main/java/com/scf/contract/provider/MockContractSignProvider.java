package com.scf.contract.provider;

import com.scf.contract.provider.model.SignRequestContext;
import com.scf.contract.provider.model.SignRequestResult;
import com.scf.contract.provider.model.SignStatusResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MockContractSignProvider implements ContractSignProvider {

    public static final String CODE = "MOCK";

    private final ConcurrentMap<String, String> statusByExternalRef = new ConcurrentHashMap<>();

    @Override
    public String providerCode() {
        return CODE;
    }

    @Override
    public String displayName() {
        return "Mock 签章（开发/联调）";
    }

    @Override
    public String description() {
        return "本地 Mock 供应商，异步回调由集成测试或手工 POST sign-callback 模拟；主动查单读取内存状态。";
    }

    @Override
    public SignRequestResult createSignRequest(SignRequestContext context) {
        String externalRef = "MOCK-SIGN-" + context.taskId();
        if (context.simulateFailure()) {
            return new SignRequestResult(externalRef, "SUBMIT_FAILED", "Mock provider simulated submit failure");
        }
        statusByExternalRef.put(externalRef, "PENDING");
        return new SignRequestResult(externalRef, "PENDING_CALLBACK", "等待签署方完成签署并回调");
    }

    @Override
    public SignStatusResult querySignStatus(String externalSignRef) {
        String status = statusByExternalRef.getOrDefault(externalSignRef, "UNKNOWN");
        return switch (status) {
            case "SUCCESS" -> new SignStatusResult(externalSignRef, "SUCCESS", Instant.now(), null);
            case "FAILED" -> new SignStatusResult(externalSignRef, "FAILED", null, "Mock provider sign failed");
            case "PENDING" -> new SignStatusResult(externalSignRef, "PENDING", null, null);
            default -> new SignStatusResult(externalSignRef, "UNKNOWN", null, null);
        };
    }

    public void simulateProviderStatus(String externalSignRef, String status) {
        if (externalSignRef == null || externalSignRef.isBlank()) {
            return;
        }
        statusByExternalRef.put(externalSignRef.trim(), status);
    }

    public void clearSimulatedStatus(String externalSignRef) {
        if (externalSignRef != null) {
            statusByExternalRef.remove(externalSignRef.trim());
        }
    }
}
