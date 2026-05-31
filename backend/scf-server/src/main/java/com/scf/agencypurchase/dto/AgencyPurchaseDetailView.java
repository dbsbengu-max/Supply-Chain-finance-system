package com.scf.agencypurchase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.agencypurchase.entity.ApAgencyPurchaseApplication;

import java.time.Instant;
import java.util.List;

public record AgencyPurchaseDetailView(
        String id,
        @JsonProperty("application_no") String applicationNo,
        @JsonProperty("order_mode") String orderMode,
        @JsonProperty("fund_source") String fundSource,
        @JsonProperty("pickup_type") String pickupType,
        @JsonProperty("mode_key") String modeKey,
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("trade_company_id") String tradeCompanyId,
        @JsonProperty("order_id") String orderId,
        String currency,
        @JsonProperty("total_amount") String totalAmount,
        @JsonProperty("application_status") String applicationStatus,
        String remark,
        @JsonProperty("bpm_instance_id") String bpmInstanceId,
        @JsonProperty("inventory_id") String inventoryId,
        @JsonProperty("margin_account_id") String marginAccountId,
        @JsonProperty("margin_amount") String marginAmount,
        @JsonProperty("margin_frozen_amount") String marginFrozenAmount,
        @JsonProperty("inventory_freeze_quantity") String inventoryFreezeQuantity,
        @JsonProperty("finance_application_id") String financeApplicationId,
        @JsonProperty("saga_status") String sagaStatus,
        @JsonProperty("saga_last_error") String sagaLastError,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("saga_steps") List<AgencyPurchaseSagaStepView> sagaSteps,
        @JsonProperty("compensation_tasks") List<AgencyPurchaseCompensationTaskView> compensationTasks
) {
    public static AgencyPurchaseDetailView from(
            ApAgencyPurchaseApplication app,
            List<AgencyPurchaseSagaStepView> sagaSteps,
            List<AgencyPurchaseCompensationTaskView> compensationTasks) {
        return new AgencyPurchaseDetailView(
                app.getId(),
                app.getApplicationNo(),
                app.getOrderMode(),
                app.getFundSource(),
                app.getPickupType(),
                app.getModeKey(),
                app.getCustomerId(),
                app.getTradeCompanyId(),
                app.getOrderId(),
                app.getCurrency(),
                app.getTotalAmount().toPlainString(),
                app.getApplicationStatus(),
                app.getRemark(),
                app.getBpmInstanceId(),
                app.getInventoryId(),
                app.getMarginAccountId(),
                money(app.getMarginAmount()),
                money(app.getMarginFrozenAmount()),
                qty(app.getInventoryFreezeQuantity()),
                app.getFinanceApplicationId(),
                app.getSagaStatus(),
                app.getSagaLastError(),
                app.getCreatedBy(),
                app.getCreatedAt(),
                app.getUpdatedAt(),
                sagaSteps,
                compensationTasks);
    }

    private static String money(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String qty(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
