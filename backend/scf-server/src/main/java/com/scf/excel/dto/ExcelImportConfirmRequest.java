package com.scf.excel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ExcelImportConfirmRequest(
        @NotBlank @JsonProperty("batch_id") String batchId,
        @JsonProperty("ignore_warning") Boolean ignoreWarning
) {
}
