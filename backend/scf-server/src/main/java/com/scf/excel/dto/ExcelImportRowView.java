package com.scf.excel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.excel.entity.ExcelImportRow;

public record ExcelImportRowView(
        @JsonProperty("row_no") int rowNo,
        @JsonProperty("row_status") String rowStatus,
        @JsonProperty("row_data") String rowData,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("warning_message") String warningMessage
) {
    public static ExcelImportRowView from(ExcelImportRow row) {
        return new ExcelImportRowView(
                row.getRowNo(),
                row.getRowStatus(),
                row.getRowData(),
                row.getErrorMessage(),
                row.getWarningMessage());
    }
}
