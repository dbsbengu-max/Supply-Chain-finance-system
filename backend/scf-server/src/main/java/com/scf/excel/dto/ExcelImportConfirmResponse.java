package com.scf.excel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExcelImportConfirmResponse(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("batch_id") String batchId,
        @JsonProperty("imported_rows") int importedRows,
        @JsonProperty("applied_to_business") boolean appliedToBusiness,
        String message
) {
}
