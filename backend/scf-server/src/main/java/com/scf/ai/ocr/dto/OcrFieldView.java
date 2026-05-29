package com.scf.ai.ocr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.ai.ocr.entity.AiOcrField;

import java.math.BigDecimal;

public record OcrFieldView(
        @JsonProperty("field_name") String fieldName,
        @JsonProperty("suggested_value") String suggestedValue,
        BigDecimal confidence,
        @JsonProperty("source_text") String sourceText,
        @JsonProperty("page_no") Integer pageNo,
        String bbox,
        @JsonProperty("confirm_status") String confirmStatus,
        @JsonProperty("confirmed_value") String confirmedValue,
        @JsonProperty("requires_manual_confirm") boolean requiresManualConfirm
) {
    private static final BigDecimal THRESHOLD = new BigDecimal("0.85");

    public static OcrFieldView from(AiOcrField field) {
        boolean manual = field.getConfidence().compareTo(THRESHOLD) < 0;
        return new OcrFieldView(
                field.getFieldName(),
                field.getSuggestedValue(),
                field.getConfidence(),
                field.getSourceText(),
                field.getPageNo(),
                field.getBbox(),
                field.getConfirmStatus(),
                field.getConfirmedValue(),
                manual);
    }
}
