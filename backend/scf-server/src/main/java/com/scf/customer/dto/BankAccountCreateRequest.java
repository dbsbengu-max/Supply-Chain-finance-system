package com.scf.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record BankAccountCreateRequest(
        @NotBlank @JsonProperty("account_type") String accountType,
        @NotBlank @JsonProperty("bank_name") String bankName,
        @NotBlank @JsonProperty("account_name") String accountName,
        @NotBlank @JsonProperty("account_no") String accountNo,
        @NotBlank String currency,
        @JsonProperty("is_repayment_account") Boolean isRepaymentAccount
) {
}
