package com.scf.bi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public final class BiDashboardDtos {

    private BiDashboardDtos() {}

    public record BiOverviewView(
            @JsonProperty("as_of") Instant asOf,
            @JsonProperty("operator_id") String operatorId,
            @JsonProperty("project_id") String projectId,
            String currency,
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("order_amount_total") String orderAmountTotal,
            @JsonProperty("finance_count") long financeCount,
            @JsonProperty("outstanding_finance_count") long outstandingFinanceCount,
            @JsonProperty("outstanding_finance_amount") String outstandingFinanceAmount,
            @JsonProperty("disbursed_amount_total") String disbursedAmountTotal,
            @JsonProperty("clearing_executed_count") long clearingExecutedCount,
            @JsonProperty("repaid_amount_total") String repaidAmountTotal,
            @JsonProperty("inventory_lot_count") long inventoryLotCount,
            @JsonProperty("inventory_valuation_total") String inventoryValuationTotal,
            @JsonProperty("voucher_finance_count") long voucherFinanceCount,
            @JsonProperty("risk_alert_count") long riskAlertCount) {}

    public record BiTradeTrendView(
            String currency,
            @JsonProperty("months") int months,
            List<BiTrendPoint> points) {}

    public record BiTrendPoint(
            String period,
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("amount_total") String amountTotal,
            String currency) {}

    public record BiFinanceSummaryView(
            String currency,
            @JsonProperty("total_count") long totalCount,
            @JsonProperty("by_status") List<BiStatusBucket> byStatus,
            @JsonProperty("disbursement_count") long disbursementCount,
            @JsonProperty("disbursement_success_count") long disbursementSuccessCount,
            @JsonProperty("disbursement_success_amount") String disbursementSuccessAmount,
            @JsonProperty("voucher_finance_count") long voucherFinanceCount,
            @JsonProperty("voucher_finance_amount") String voucherFinanceAmount) {}

    public record BiWarehouseSummaryView(
            String currency,
            @JsonProperty("inventory_lot_count") long inventoryLotCount,
            @JsonProperty("quantity_total") String quantityTotal,
            @JsonProperty("available_quantity_total") String availableQuantityTotal,
            @JsonProperty("pledged_quantity_total") String pledgedQuantityTotal,
            @JsonProperty("valuation_total") String valuationTotal,
            @JsonProperty("by_right_status") List<BiStatusBucket> byRightStatus,
            @JsonProperty("stocktake_exception_count") long stocktakeExceptionCount) {}

    public record BiClearingSummaryView(
            String currency,
            @JsonProperty("executed_clearing_count") long executedClearingCount,
            @JsonProperty("repaid_principal_total") String repaidPrincipalTotal,
            @JsonProperty("repaid_interest_total") String repaidInterestTotal,
            @JsonProperty("repaid_fee_total") String repaidFeeTotal,
            @JsonProperty("repayment_count") long repaymentCount,
            @JsonProperty("unmatched_inflow_count") long unmatchedInflowCount,
            @JsonProperty("unmatched_inflow_amount") String unmatchedInflowAmount) {}

    public record BiRiskAlertsView(
            @JsonProperty("alert_count") long alertCount,
            List<BiRiskAlertItem> alerts) {}

    public record BiRiskAlertItem(
            String code,
            String severity,
            String title,
            String message,
            @JsonProperty("related_id") String relatedId,
            @JsonProperty("related_type") String relatedType) {}

    public record BiStatusBucket(
            String status,
            long count,
            @JsonProperty("amount_total") String amountTotal,
            String currency) {}
}
