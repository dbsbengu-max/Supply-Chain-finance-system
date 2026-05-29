package com.scf.voucher.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.voucher.entity.DvVoucher;
import com.scf.voucher.entity.DvVoucherFlow;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

public final class VoucherDtos {

    private VoucherDtos() {
    }

    public record VoucherCreateRequest(
            @JsonProperty("issuer_id") String issuerId,
            @JsonProperty("acceptor_id") @NotBlank String acceptorId,
            @JsonProperty("holder_id") String holderId,
            @JsonProperty("amount") @NotBlank String amount,
            @JsonProperty("currency") @NotBlank String currency,
            @JsonProperty("issue_date") LocalDate issueDate,
            @JsonProperty("due_date") LocalDate dueDate) {}

    public record VoucherTransferRequest(
            @JsonProperty("to_holder_id") @NotBlank String toHolderId,
            @JsonProperty("remark") String remark) {}

    public record VoucherSplitRequest(
            @JsonProperty("amount") String amount,
            @JsonProperty("split_amount") String splitAmount,
            @JsonProperty("to_holder_id") String toHolderId,
            @JsonProperty("remark") String remark) {}

    public record VoucherRedeemRequest(
            @JsonProperty("remark") String remark) {}

    public record VoucherView(
            String id,
            @JsonProperty("voucher_no") String voucherNo,
            @JsonProperty("issuer_id") String issuerId,
            @JsonProperty("acceptor_id") String acceptorId,
            @JsonProperty("holder_id") String holderId,
            @JsonProperty("parent_voucher_id") String parentVoucherId,
            String amount,
            @JsonProperty("available_amount") String availableAmount,
            String currency,
            @JsonProperty("issue_date") LocalDate issueDate,
            @JsonProperty("due_date") LocalDate dueDate,
            @JsonProperty("voucher_status") String voucherStatus,
            @JsonProperty("evidence_status") String evidenceStatus,
            @JsonProperty("version_no") Integer versionNo) {
        public static VoucherView from(DvVoucher voucher) {
            return new VoucherView(
                    voucher.getId(),
                    voucher.getVoucherNo(),
                    voucher.getIssuerId(),
                    voucher.getAcceptorId(),
                    voucher.getHolderId(),
                    voucher.getParentVoucherId(),
                    money(voucher.getAmount()),
                    money(voucher.getAvailableAmount()),
                    voucher.getCurrency(),
                    voucher.getIssueDate(),
                    voucher.getDueDate(),
                    voucher.getVoucherStatus(),
                    voucher.getEvidenceStatus(),
                    voucher.getVersionNo());
        }
    }

    public record VoucherFlowView(
            String id,
            @JsonProperty("voucher_id") String voucherId,
            @JsonProperty("flow_type") String flowType,
            @JsonProperty("from_holder_id") String fromHolderId,
            @JsonProperty("to_holder_id") String toHolderId,
            String amount,
            @JsonProperty("before_available_amount") String beforeAvailableAmount,
            @JsonProperty("after_available_amount") String afterAvailableAmount,
            @JsonProperty("related_voucher_id") String relatedVoucherId,
            @JsonProperty("operated_by") String operatedBy,
            @JsonProperty("operated_at") Instant operatedAt) {
        public static VoucherFlowView from(DvVoucherFlow flow) {
            return new VoucherFlowView(
                    flow.getId(),
                    flow.getVoucherId(),
                    flow.getFlowType(),
                    flow.getFromHolderId(),
                    flow.getToHolderId(),
                    money(flow.getAmount()),
                    money(flow.getBeforeAvailableAmount()),
                    money(flow.getAfterAvailableAmount()),
                    flow.getRelatedVoucherId(),
                    flow.getOperatedBy(),
                    flow.getOperatedAt());
        }
    }

    public record VoucherDetailView(
            String id,
            @JsonProperty("voucher_no") String voucherNo,
            @JsonProperty("issuer_id") String issuerId,
            @JsonProperty("acceptor_id") String acceptorId,
            @JsonProperty("holder_id") String holderId,
            @JsonProperty("parent_voucher_id") String parentVoucherId,
            String amount,
            @JsonProperty("available_amount") String availableAmount,
            String currency,
            @JsonProperty("issue_date") LocalDate issueDate,
            @JsonProperty("due_date") LocalDate dueDate,
            @JsonProperty("voucher_status") String voucherStatus,
            @JsonProperty("evidence_status") String evidenceStatus,
            @JsonProperty("version_no") Integer versionNo,
            VoucherView voucher,
            List<VoucherFlowView> flows,
            @JsonProperty("finance_summary") VoucherFinanceSummaryView financeSummary) {
        public static VoucherDetailView from(
                VoucherView voucher, List<VoucherFlowView> flows, VoucherFinanceSummaryView financeSummary) {
            return new VoucherDetailView(
                    voucher.id(),
                    voucher.voucherNo(),
                    voucher.issuerId(),
                    voucher.acceptorId(),
                    voucher.holderId(),
                    voucher.parentVoucherId(),
                    voucher.amount(),
                    voucher.availableAmount(),
                    voucher.currency(),
                    voucher.issueDate(),
                    voucher.dueDate(),
                    voucher.voucherStatus(),
                    voucher.evidenceStatus(),
                    voucher.versionNo(),
                    voucher,
                    flows,
                    financeSummary);
        }
    }

    public record VoucherFinanceSummaryView(
            @JsonProperty("finance_occupied_amount") String financeOccupiedAmount,
            @JsonProperty("released_amount") String releasedAmount,
            @JsonProperty("pending_redeem_amount") String pendingRedeemAmount) {}

    public record RepaymentSettledPayload(
            @JsonProperty("finance_id") String financeId,
            @JsonProperty("repayment_id") String repaymentId,
            @JsonProperty("product_type") String productType,
            @JsonProperty("source_type") String sourceType,
            @JsonProperty("source_id") String sourceId,
            @JsonProperty("project_id") String projectId,
            @JsonProperty("operator_id") String operatorId,
            @JsonProperty("customer_id") String customerId,
            @JsonProperty("principal_amount") String principalAmount,
            @JsonProperty("finance_status") String financeStatus) {}

    public record FinanceDisbursedPayload(
            @JsonProperty("finance_id") String financeId,
            @JsonProperty("product_type") String productType,
            @JsonProperty("source_type") String sourceType,
            @JsonProperty("source_id") String sourceId,
            @JsonProperty("project_id") String projectId,
            @JsonProperty("operator_id") String operatorId,
            @JsonProperty("disbursed_amount") String disbursedAmount,
            @JsonProperty("customer_id") String customerId) {}

    private static String money(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
