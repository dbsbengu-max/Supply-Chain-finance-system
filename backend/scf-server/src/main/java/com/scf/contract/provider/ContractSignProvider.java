package com.scf.contract.provider;

import com.scf.contract.provider.model.SignRequestContext;
import com.scf.contract.provider.model.SignRequestResult;
import com.scf.contract.provider.model.SignStatusResult;

public interface ContractSignProvider {

    String providerCode();

    default String displayName() {
        return providerCode();
    }

    default String description() {
        return "";
    }

    default boolean supportsStatusQuery() {
        return true;
    }

    SignRequestResult createSignRequest(SignRequestContext context);

    SignStatusResult querySignStatus(String externalSignRef);
}
