package com.scf.contract.provider;

import com.scf.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ContractSignProviderRegistry {

    private final Map<String, ContractSignProvider> providersByCode;

    public ContractSignProviderRegistry(List<ContractSignProvider> providers) {
        this.providersByCode = providers.stream()
                .collect(Collectors.toMap(ContractSignProvider::providerCode, Function.identity()));
    }

    public ContractSignProvider require(String providerCode) {
        ContractSignProvider provider = providersByCode.get(providerCode);
        if (provider == null) {
            throw new BusinessException("VALID_400", "不支持的签章供应商：" + providerCode, 400);
        }
        return provider;
    }

    public List<ContractSignProvider> listProviders() {
        return providersByCode.values().stream()
                .sorted((a, b) -> a.providerCode().compareTo(b.providerCode()))
                .toList();
    }
}
