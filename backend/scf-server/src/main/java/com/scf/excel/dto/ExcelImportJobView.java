package com.scf.excel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record ExcelImportJobView(
        String id,
        @JsonProperty("file_id") String fileId,
        @JsonProperty("import_type") String importType,
        @JsonProperty("batch_id") String batchId,
        @JsonProperty("dry_run") boolean dryRun,
        String status,
        @JsonProperty("total_rows") int totalRows,
        @JsonProperty("ok_rows") int okRows,
        @JsonProperty("error_rows") int errorRows,
        @JsonProperty("warning_rows") int warningRows,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("confirmed_by") String confirmedBy,
        @JsonProperty("confirmed_at") Instant confirmedAt,
        List<ExcelImportRowView> rows
) {
}
