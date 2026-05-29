package com.scf.excel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ExcelImportCreateRequest(
        @NotBlank @JsonProperty("file_id") String fileId,
        @NotBlank @JsonProperty("import_type") String importType,
        @JsonProperty("mapping_profile_id") String mappingProfileId,
        @JsonProperty("dry_run") Boolean dryRun
) {
}
