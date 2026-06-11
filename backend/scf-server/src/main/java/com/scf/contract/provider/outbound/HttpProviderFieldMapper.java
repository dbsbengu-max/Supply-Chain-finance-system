package com.scf.contract.provider.outbound;

import com.scf.contract.config.ContractSignProperties.HttpProvider;
import com.scf.contract.config.ContractSignProperties.HttpProviderFieldMapping;
import com.scf.contract.provider.model.SignRequestContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HttpProviderFieldMapper {

    public Map<String, Object> toCreatePayload(HttpProvider config, SignRequestContext context) {
        HttpProviderFieldMapping mapping = config.getFieldMapping();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(mapping.getTaskId(), context.taskId());
        payload.put(mapping.getDocumentId(), context.documentId());
        payload.put(mapping.getFileId(), context.fileId());
        payload.put(mapping.getDocumentNo(), context.documentNo());
        payload.put(mapping.getBusinessType(), context.businessType());
        payload.put(mapping.getBusinessId(), context.businessId());
        payload.put(mapping.getSigners(), mapSigners(context, mapping));
        return payload;
    }

    private static List<Map<String, String>> mapSigners(SignRequestContext context, HttpProviderFieldMapping mapping) {
        if (context.signers() == null) {
            return List.of();
        }
        return context.signers().stream()
                .map(s -> Map.of(
                        mapping.getSignerEnterpriseId(), s.enterpriseId() == null ? "" : s.enterpriseId(),
                        mapping.getSignerName(), s.signerName() == null ? "" : s.signerName(),
                        mapping.getSignerRole(), s.signerRole() == null ? "" : s.signerRole()))
                .toList();
    }
}
