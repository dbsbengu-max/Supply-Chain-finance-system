package com.scf.contract;

import com.scf.contract.config.ContractSignProperties;
import com.scf.contract.provider.outbound.HttpProviderFieldMapper;
import com.scf.contract.provider.model.SignRequestContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpProviderFieldMapperTest {

    private final HttpProviderFieldMapper mapper = new HttpProviderFieldMapper();

    @Test
    void ea045DefaultFieldMappingUsesPlatformNames() {
        ContractSignProperties.HttpProvider config = new ContractSignProperties.HttpProvider();
        Map<String, Object> payload = mapper.toCreatePayload(config, new SignRequestContext(
                "TASK_EA045",
                "DOC_EA045",
                "FILE_EA045",
                "CONTRACT-045",
                "TRADE_ORDER",
                "ORD_EA045",
                List.of(new SignRequestContext.SignerRef("ENT_A", "Alice", "BUYER")),
                false));

        assertThat(payload.get("task_id")).isEqualTo("TASK_EA045");
        assertThat(payload.get("document_id")).isEqualTo("DOC_EA045");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> signers = (List<Map<String, String>>) payload.get("signers");
        assertThat(signers.get(0)).containsEntry("enterprise_id", "ENT_A");
    }

    @Test
    void ea045VendorFieldMappingCanBeConfigured() {
        ContractSignProperties.HttpProvider config = new ContractSignProperties.HttpProvider();
        ContractSignProperties.HttpProviderFieldMapping mapping = config.getFieldMapping();
        mapping.setTaskId("bizTaskNo");
        mapping.setDocumentId("contractId");
        mapping.setSignerEnterpriseId("orgCode");

        Map<String, Object> payload = mapper.toCreatePayload(config, new SignRequestContext(
                "TASK_VENDOR",
                "DOC_VENDOR",
                "FILE_VENDOR",
                "NO-1",
                "ORDER",
                "ORD-1",
                List.of(new SignRequestContext.SignerRef("ORG-001", "Bob", "SELLER")),
                false));

        assertThat(payload.get("bizTaskNo")).isEqualTo("TASK_VENDOR");
        assertThat(payload.get("contractId")).isEqualTo("DOC_VENDOR");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> signers = (List<Map<String, String>>) payload.get("signers");
        assertThat(signers.get(0)).containsEntry("orgCode", "ORG-001");
    }
}
