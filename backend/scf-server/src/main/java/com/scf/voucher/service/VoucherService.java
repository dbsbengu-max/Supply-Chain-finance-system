package com.scf.voucher.service;

import com.scf.audit.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.common.dto.PageResponse;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.finance.entity.FnFinanceApplication;
import com.scf.finance.repository.FnFinanceApplicationRepository;
import com.scf.saga.entity.BizEventOutbox;
import com.scf.voucher.dto.VoucherDtos.FinanceDisbursedPayload;
import com.scf.voucher.dto.VoucherDtos.RepaymentSettledPayload;
import com.scf.voucher.dto.VoucherDtos.VoucherCreateRequest;
import com.scf.voucher.dto.VoucherDtos.VoucherDetailView;
import com.scf.voucher.dto.VoucherDtos.VoucherFinanceSummaryView;
import com.scf.voucher.dto.VoucherDtos.VoucherFlowView;
import com.scf.voucher.dto.VoucherDtos.VoucherRedeemRequest;
import com.scf.voucher.dto.VoucherDtos.VoucherSplitRequest;
import com.scf.voucher.dto.VoucherDtos.VoucherTransferRequest;
import com.scf.voucher.dto.VoucherDtos.VoucherView;
import com.scf.voucher.entity.DvVoucher;
import com.scf.voucher.entity.DvVoucherFlow;
import com.scf.voucher.repository.DvVoucherFlowRepository;
import com.scf.voucher.repository.DvVoucherRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class VoucherService {

    private final DvVoucherRepository voucherRepository;
    private final DvVoucherFlowRepository flowRepository;
    private final FnFinanceApplicationRepository financeRepository;
    private final TenantContext tenantContext;
    private final DataScopeHelper dataScopeHelper;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public VoucherService(
            DvVoucherRepository voucherRepository,
            DvVoucherFlowRepository flowRepository,
            FnFinanceApplicationRepository financeRepository,
            TenantContext tenantContext,
            DataScopeHelper dataScopeHelper,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.voucherRepository = voucherRepository;
        this.flowRepository = flowRepository;
        this.financeRepository = financeRepository;
        this.tenantContext = tenantContext;
        this.dataScopeHelper = dataScopeHelper;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    public PageResponse<VoucherView> list(int pageNo, int pageSize, String status) {
        tenantContext.requirePermission("VOUCHER_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        UserContext user = SecurityUtils.currentUser();
        PageRequest pageable = PageRequest.of(Math.max(pageNo - 1, 0), Math.max(pageSize, 1));

        Page<DvVoucher> page;
        if (dataScopeHelper.canReadOperatorData(user) || dataScopeHelper.isFundingRole(user)) {
            page = voucherRepository.findProjectScope(operatorId, projectId, blankToNull(status), null, null, pageable);
        } else if (dataScopeHelper.isEnterpriseRole(user)) {
            page = voucherRepository.findEnterpriseScope(operatorId, projectId, user.enterpriseId(), blankToNull(status), null, pageable);
        } else {
            throw new BusinessException("AUTH_403", "无凭证数据范围", 403);
        }

        List<VoucherView> records = page.getContent().stream()
                .map(VoucherView::from)
                .toList();
        return PageResponse.of(pageNo, pageSize, page.getTotalElements(), records);
    }

    public VoucherDetailView get(String id) {
        tenantContext.requirePermission("VOUCHER_VIEW");
        DvVoucher voucher = loadAccessible(id);
        return detail(voucher);
    }

    @Transactional
    public VoucherDetailView create(VoucherCreateRequest request) {
        tenantContext.requirePermission("VOUCHER_CREATE");
        UserContext user = SecurityUtils.currentUser();
        String holderId = request.holderId() == null || request.holderId().isBlank()
                ? user.enterpriseId()
                : request.holderId();
        String issuerId = request.issuerId() == null || request.issuerId().isBlank()
                ? holderId
                : request.issuerId();
        if (dataScopeHelper.isEnterpriseRole(user) && !user.enterpriseId().equals(holderId)) {
            throw new BusinessException("AUTH_403", "成员企业仅可创建本企业持有的凭证", 403);
        }

        BigDecimal amount = parsePositiveAmount(request.amount());
        LocalDate issueDate = request.issueDate() == null ? LocalDate.now() : request.issueDate();
        LocalDate dueDate = request.dueDate();
        if (dueDate == null || !dueDate.isAfter(issueDate)) {
            throw new BusinessException("VALID_400", "到期日必须晚于签发日", 400);
        }

        DvVoucher voucher = new DvVoucher();
        voucher.setId(IdGenerator.nextId());
        voucher.setOperatorId(tenantContext.requireOperatorId());
        voucher.setProjectId(tenantContext.requireProjectId());
        voucher.setVoucherNo("DV-" + System.currentTimeMillis());
        voucher.setIssuerId(issuerId);
        voucher.setAcceptorId(request.acceptorId());
        voucher.setHolderId(holderId);
        voucher.setAmount(amount);
        voucher.setAvailableAmount(amount);
        voucher.setCurrency(request.currency());
        voucher.setIssueDate(issueDate);
        voucher.setDueDate(dueDate);
        voucher.setVoucherStatus("DRAFT");
        voucher.setEvidenceStatus("PENDING");
        voucher.setVersionNo(1);
        voucherRepository.save(voucher);
        addFlow(voucher, "CREATE", null, voucher.getHolderId(), amount, BigDecimal.ZERO, amount, null, user.userId());
        auditLogService.log("VOUCHER_CREATE", "VOUCHER", voucher.getId(), null, auditMap(voucher));
        return detail(voucher);
    }

    @Transactional
    public VoucherDetailView issue(String id) {
        tenantContext.requirePermission("VOUCHER_ISSUE");
        DvVoucher voucher = loadAccessibleForUpdate(id);
        if (!"DRAFT".equals(voucher.getVoucherStatus())) {
            throw new BusinessException("STATE_409", "仅草稿凭证可签发", 409);
        }
        voucher.setVoucherStatus("ISSUED");
        voucher.setEvidenceStatus("SUCCESS");
        voucher.setVersionNo(voucher.getVersionNo() + 1);
        voucherRepository.save(voucher);
        addFlow(voucher, "ISSUE", null, voucher.getHolderId(), voucher.getAmount(), BigDecimal.ZERO,
                voucher.getAvailableAmount(), null, SecurityUtils.currentUserId());
        auditLogService.log("VOUCHER_ISSUE", "VOUCHER", voucher.getId(), null, auditMap(voucher));
        return detail(voucher);
    }

    @Transactional
    public VoucherDetailView transfer(String id, VoucherTransferRequest request) {
        tenantContext.requirePermission("VOUCHER_TRANSFER");
        DvVoucher voucher = loadAccessibleForUpdate(id);
        assertActive(voucher);
        UserContext user = SecurityUtils.currentUser();
        if (dataScopeHelper.isEnterpriseRole(user) && !user.enterpriseId().equals(voucher.getHolderId())) {
            throw new BusinessException("AUTH_403", "仅当前持有人可转让凭证", 403);
        }
        String beforeHolder = voucher.getHolderId();
        voucher.setHolderId(request.toHolderId());
        voucher.setVoucherStatus("TRANSFERRED");
        voucher.setVersionNo(voucher.getVersionNo() + 1);
        voucherRepository.save(voucher);
        addFlow(voucher, "TRANSFER", beforeHolder, request.toHolderId(), voucher.getAvailableAmount(),
                voucher.getAvailableAmount(), voucher.getAvailableAmount(), null, user.userId());
        auditLogService.log("TRANSFER", "VOUCHER", voucher.getId(),
                Map.of("holder_id", beforeHolder), auditMap(voucher));
        return detail(voucher);
    }

    @Transactional
    public VoucherDetailView split(String id, VoucherSplitRequest request) {
        tenantContext.requirePermission("VOUCHER_SPLIT");
        DvVoucher parent = loadAccessibleForUpdate(id);
        assertActive(parent);
        UserContext user = SecurityUtils.currentUser();
        if (dataScopeHelper.isEnterpriseRole(user) && !user.enterpriseId().equals(parent.getHolderId())) {
            throw new BusinessException("AUTH_403", "仅当前持有人可拆分凭证", 403);
        }
        String rawAmount = request.splitAmount() == null || request.splitAmount().isBlank()
                ? request.amount()
                : request.splitAmount();
        BigDecimal splitAmount = parsePositiveAmount(rawAmount);
        if (splitAmount.compareTo(parent.getAvailableAmount()) >= 0) {
            throw new BusinessException("VALID_400", "拆分金额必须小于可用余额", 400);
        }
        BigDecimal before = parent.getAvailableAmount();
        parent.setAvailableAmount(before.subtract(splitAmount));
        parent.setVersionNo(parent.getVersionNo() + 1);
        voucherRepository.save(parent);

        DvVoucher child = new DvVoucher();
        child.setId(IdGenerator.nextId());
        child.setOperatorId(parent.getOperatorId());
        child.setProjectId(parent.getProjectId());
        child.setVoucherNo("DV-S-" + System.currentTimeMillis());
        child.setIssuerId(parent.getIssuerId());
        child.setAcceptorId(parent.getAcceptorId());
        child.setHolderId(request.toHolderId() == null || request.toHolderId().isBlank()
                ? parent.getHolderId()
                : request.toHolderId());
        child.setParentVoucherId(parent.getId());
        child.setAmount(splitAmount);
        child.setAvailableAmount(splitAmount);
        child.setCurrency(parent.getCurrency());
        child.setIssueDate(parent.getIssueDate());
        child.setDueDate(parent.getDueDate());
        child.setVoucherStatus("ISSUED");
        child.setEvidenceStatus(parent.getEvidenceStatus());
        child.setVersionNo(1);
        voucherRepository.save(child);

        addFlow(parent, "SPLIT_OUT", parent.getHolderId(), child.getHolderId(), splitAmount,
                before, parent.getAvailableAmount(), child.getId(), user.userId());
        addFlow(child, "SPLIT_IN", parent.getHolderId(), child.getHolderId(), splitAmount,
                BigDecimal.ZERO, splitAmount, parent.getId(), user.userId());
        auditLogService.log("VOUCHER_SPLIT", "VOUCHER", parent.getId(),
                Map.of("available_amount", before.toPlainString()),
                Map.of("available_amount", parent.getAvailableAmount().toPlainString(), "child_voucher_id", child.getId()));
        return detail(parent);
    }

    @Transactional
    public VoucherDetailView redeemApply(String id, VoucherRedeemRequest request) {
        tenantContext.requirePermission("VOUCHER_REDEEM");
        DvVoucher voucher = loadAccessibleForUpdate(id);
        assertActive(voucher);
        voucher.setVoucherStatus("REDEEM_PENDING");
        voucher.setVersionNo(voucher.getVersionNo() + 1);
        voucherRepository.save(voucher);
        addFlow(voucher, "REDEEM_APPLY", voucher.getHolderId(), voucher.getAcceptorId(),
                voucher.getAvailableAmount(), voucher.getAvailableAmount(), voucher.getAvailableAmount(),
                null, SecurityUtils.currentUserId());
        auditLogService.log("REDEEM_APPLY", "VOUCHER", voucher.getId(), null, auditMap(voucher));
        return detail(voucher);
    }

    @Transactional
    public VoucherDetailView cancel(String id) {
        tenantContext.requirePermission("VOUCHER_CANCEL");
        DvVoucher voucher = loadAccessibleForUpdate(id);
        if (!"DRAFT".equals(voucher.getVoucherStatus())
                && !"ACCEPTED".equals(voucher.getVoucherStatus())
                && !"ISSUED".equals(voucher.getVoucherStatus())
                && !"TRANSFERRED".equals(voucher.getVoucherStatus())) {
            throw new BusinessException("STATE_409", "当前状态不可作废", 409);
        }
        String before = voucher.getVoucherStatus();
        voucher.setVoucherStatus("CANCELLED");
        voucher.setVersionNo(voucher.getVersionNo() + 1);
        voucherRepository.save(voucher);
        addFlow(voucher, "CANCEL", voucher.getHolderId(), null, voucher.getAvailableAmount(),
                voucher.getAvailableAmount(), voucher.getAvailableAmount(), null, SecurityUtils.currentUserId());
        auditLogService.log("VOUCHER_CANCEL", "VOUCHER", voucher.getId(),
                Map.of("voucher_status", before), auditMap(voucher));
        return detail(voucher);
    }

    @Transactional
    public void handleFinanceDisbursed(BizEventOutbox event) {
        try {
            FinanceDisbursedPayload payload = objectMapper.readValue(event.getPayloadJson(), FinanceDisbursedPayload.class);
            if (!"VOUCHER".equals(payload.sourceType()) || payload.sourceId() == null || payload.sourceId().isBlank()) {
                return;
            }
            linkFinanceDisbursedToVoucher(
                    payload.financeId(),
                    payload.sourceId(),
                    payload.operatorId(),
                    payload.projectId(),
                    payload.customerId(),
                    payload.disbursedAmount());
        } catch (Exception ex) {
            throw new BusinessException("SAGA_500", "凭证 Saga 处理失败: " + ex.getMessage(), 500);
        }
    }

    @Transactional
    public void handleRepaymentSettled(BizEventOutbox event) {
        try {
            RepaymentSettledPayload payload = objectMapper.readValue(event.getPayloadJson(), RepaymentSettledPayload.class);
            if (!"VOUCHER".equals(payload.sourceType()) || payload.sourceId() == null || payload.sourceId().isBlank()) {
                return;
            }
            releaseFinanceLockFromRepayment(
                    payload.repaymentId(),
                    payload.financeId(),
                    payload.sourceId(),
                    payload.operatorId(),
                    payload.projectId(),
                    payload.customerId(),
                    payload.principalAmount(),
                    payload.financeStatus());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("SAGA_500", "凭证释放 Saga 处理失败: " + ex.getMessage(), 500);
        }
    }

    @Transactional
    public void issueFromFinanceDisbursed(String financeId) {
        FnFinanceApplication app = financeRepository.findById(financeId)
                .orElseThrow(() -> new BusinessException("DATA_404", "融资申请不存在", 404));
        if (!"VOUCHER".equals(app.getSourceType()) || app.getSourceId() == null) {
            return;
        }
        DvVoucher voucher = voucherRepository
                .findByIdAndOperatorIdAndProjectId(app.getSourceId(), app.getOperatorId(), app.getProjectId())
                .orElseThrow(() -> new BusinessException("DATA_404", "关联凭证不存在", 404));
        if (!"ACCEPTED".equals(voucher.getVoucherStatus())
                && !"ISSUED".equals(voucher.getVoucherStatus())
                && !"TRANSFERRED".equals(voucher.getVoucherStatus())) {
            return;
        }
        auditLogService.logAsSystem(
                "system",
                app.getOperatorId(),
                app.getFundingPartyId(),
                app.getProjectId(),
                "VOUCHER_ISSUE",
                "VOUCHER",
                voucher.getId(),
                null,
                Map.of("finance_id", app.getId(), "finance_status", app.getFinanceStatus()));
    }

    private void linkFinanceDisbursedToVoucher(
            String financeId,
            String voucherId,
            String operatorId,
            String projectId,
            String auditEnterpriseId,
            String disbursedAmount) {
        if (flowRepository.existsByVoucherIdAndFlowTypeAndRelatedVoucherId(voucherId, "FINANCE_LOCK", financeId)) {
            return;
        }
        DvVoucher voucher = voucherRepository
                .findByIdForUpdate(voucherId, operatorId, projectId)
                .orElseThrow(() -> new BusinessException("DATA_404", "关联凭证不存在", 404));
        if (!"ACCEPTED".equals(voucher.getVoucherStatus())
                && !"ISSUED".equals(voucher.getVoucherStatus())
                && !"TRANSFERRED".equals(voucher.getVoucherStatus())) {
            auditLogService.logAsSystem(
                    "system",
                    operatorId,
                    auditEnterpriseId,
                    projectId,
                    "SAGA_FINANCE_DISBURSED_VOUCHER_SKIP",
                    "VOUCHER",
                    voucher.getId(),
                    null,
                    Map.of("finance_id", financeId, "voucher_status", voucher.getVoucherStatus()));
            return;
        }
        BigDecimal financeAmount = disbursedAmount == null || disbursedAmount.isBlank()
                ? BigDecimal.ZERO
                : new BigDecimal(disbursedAmount);
        BigDecimal before = voucher.getAvailableAmount();
        BigDecimal lockedBefore = voucher.getLockedAmount() == null ? BigDecimal.ZERO : voucher.getLockedAmount();
        if (financeAmount.compareTo(BigDecimal.ZERO) > 0 && financeAmount.compareTo(before) <= 0) {
            voucher.setAvailableAmount(before.subtract(financeAmount));
            voucher.setLockedAmount(lockedBefore.add(financeAmount));
        }
        voucher.setVoucherStatus("FINANCING");
        voucher.setVersionNo(voucher.getVersionNo() + 1);
        voucherRepository.save(voucher);
        addFlow(voucher, "FINANCE_LOCK", voucher.getHolderId(), auditEnterpriseId, financeAmount,
                before, voucher.getAvailableAmount(), financeId, "system");
        auditLogService.logAsSystem(
                "system",
                operatorId,
                auditEnterpriseId,
                projectId,
                "VOUCHER_ISSUE",
                "VOUCHER",
                voucher.getId(),
                Map.of(
                        "voucher_status", "ACCEPTED",
                        "available_amount", before.toPlainString(),
                        "locked_amount", lockedBefore.toPlainString()),
                Map.of(
                        "finance_id", financeId,
                        "disbursed_amount", disbursedAmount == null ? "" : disbursedAmount,
                        "voucher_status", voucher.getVoucherStatus(),
                        "locked_amount", voucher.getLockedAmount().toPlainString()));
    }

    private void releaseFinanceLockFromRepayment(
            String repaymentId,
            String financeId,
            String voucherId,
            String operatorId,
            String projectId,
            String auditEnterpriseId,
            String principalAmount,
            String financeStatus) {
        if (flowRepository.existsByVoucherIdAndFlowTypeAndRelatedVoucherId(voucherId, "FINANCE_RELEASE", repaymentId)) {
            return;
        }
        DvVoucher voucher = voucherRepository
                .findByIdForUpdate(voucherId, operatorId, projectId)
                .orElseThrow(() -> new BusinessException("DATA_404", "关联凭证不存在", 404));
        BigDecimal locked = voucher.getLockedAmount() == null ? BigDecimal.ZERO : voucher.getLockedAmount();
        if (locked.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal principal = principalAmount == null || principalAmount.isBlank()
                ? BigDecimal.ZERO
                : new BigDecimal(principalAmount);
        BigDecimal releaseAmount;
        if ("SETTLED".equals(financeStatus)) {
            releaseAmount = locked;
        } else {
            releaseAmount = principal.min(locked);
        }
        if (releaseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String beforeStatus = voucher.getVoucherStatus();
        BigDecimal beforeAvailable = voucher.getAvailableAmount();
        voucher.setLockedAmount(locked.subtract(releaseAmount));
        voucher.setAvailableAmount(beforeAvailable.add(releaseAmount));
        if ("SETTLED".equals(financeStatus)
                && voucher.getLockedAmount().compareTo(BigDecimal.ZERO) <= 0
                && "FINANCING".equals(beforeStatus)) {
            voucher.setVoucherStatus("ACCEPTED");
        }
        voucher.setVersionNo(voucher.getVersionNo() + 1);
        voucherRepository.save(voucher);
        addFlow(voucher, "FINANCE_RELEASE", auditEnterpriseId, voucher.getHolderId(), releaseAmount,
                beforeAvailable, voucher.getAvailableAmount(), repaymentId, "system");
        auditLogService.logAsSystem(
                "system",
                operatorId,
                auditEnterpriseId,
                projectId,
                "VOUCHER_RELEASE",
                "VOUCHER",
                voucher.getId(),
                Map.of(
                        "finance_id", financeId,
                        "repayment_id", repaymentId,
                        "locked_amount", locked.toPlainString(),
                        "available_amount", beforeAvailable.toPlainString(),
                        "voucher_status", beforeStatus),
                Map.of(
                        "finance_id", financeId,
                        "repayment_id", repaymentId,
                        "released_amount", releaseAmount.toPlainString(),
                        "locked_amount", voucher.getLockedAmount().toPlainString(),
                        "available_amount", voucher.getAvailableAmount().toPlainString(),
                        "finance_status", financeStatus == null ? "" : financeStatus,
                        "voucher_status", voucher.getVoucherStatus()));
    }

    private VoucherDetailView detail(DvVoucher voucher) {
        List<VoucherFlowView> flows = flowRepository.findByVoucherIdOrderByOperatedAtDesc(voucher.getId())
                .stream()
                .map(VoucherFlowView::from)
                .toList();
        return VoucherDetailView.from(VoucherView.from(voucher), flows, buildFinanceSummary(voucher));
    }

    private VoucherFinanceSummaryView buildFinanceSummary(DvVoucher voucher) {
        BigDecimal locked = voucher.getLockedAmount() == null ? BigDecimal.ZERO : voucher.getLockedAmount();
        BigDecimal released = flowRepository.sumAmountByVoucherIdAndFlowType(voucher.getId(), "FINANCE_RELEASE");
        if (released == null) {
            released = BigDecimal.ZERO;
        }
        BigDecimal pendingRedeem = pendingRedeemAmount(voucher);
        return new VoucherFinanceSummaryView(
                money(locked),
                money(released),
                money(pendingRedeem));
    }

    private static BigDecimal pendingRedeemAmount(DvVoucher voucher) {
        String status = voucher.getVoucherStatus();
        if ("CANCELLED".equals(status) || "REDEEMED".equals(status) || "DRAFT".equals(status)) {
            return BigDecimal.ZERO;
        }
        return voucher.getAvailableAmount() == null ? BigDecimal.ZERO : voucher.getAvailableAmount();
    }

    private static String money(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private DvVoucher loadAccessible(String id) {
        DvVoucher voucher = voucherRepository
                .findByIdAndOperatorIdAndProjectId(id, tenantContext.requireOperatorId(), tenantContext.requireProjectId())
                .orElseThrow(() -> new BusinessException("DATA_404", "凭证不存在", 404));
        assertAccessible(voucher);
        return voucher;
    }

    private DvVoucher loadAccessibleForUpdate(String id) {
        DvVoucher voucher = voucherRepository
                .findByIdForUpdate(id, tenantContext.requireOperatorId(), tenantContext.requireProjectId())
                .orElseThrow(() -> new BusinessException("DATA_404", "凭证不存在", 404));
        assertAccessible(voucher);
        return voucher;
    }

    private void assertAccessible(DvVoucher voucher) {
        UserContext user = SecurityUtils.currentUser();
        if (dataScopeHelper.canReadOperatorData(user) || dataScopeHelper.isFundingRole(user)) {
            return;
        }
        if (dataScopeHelper.isEnterpriseRole(user)
                && (user.enterpriseId().equals(voucher.getIssuerId())
                || user.enterpriseId().equals(voucher.getAcceptorId())
                || user.enterpriseId().equals(voucher.getHolderId()))) {
            return;
        }
        throw new BusinessException("AUTH_403", "无权访问该凭证", 403);
    }

    private void assertActive(DvVoucher voucher) {
        if (!"ACCEPTED".equals(voucher.getVoucherStatus())
                && !"ISSUED".equals(voucher.getVoucherStatus())
                && !"TRANSFERRED".equals(voucher.getVoucherStatus())) {
            throw new BusinessException("STATE_409", "仅已签发凭证可执行该操作", 409);
        }
    }

    private void addFlow(
            DvVoucher voucher,
            String flowType,
            String fromHolderId,
            String toHolderId,
            BigDecimal amount,
            BigDecimal before,
            BigDecimal after,
            String relatedVoucherId,
            String operatedBy) {
        DvVoucherFlow flow = new DvVoucherFlow();
        flow.setId(IdGenerator.nextId());
        flow.setVoucherId(voucher.getId());
        flow.setFlowType(flowType);
        flow.setFromHolderId(fromHolderId);
        flow.setToHolderId(toHolderId);
        flow.setAmount(amount);
        flow.setBeforeAvailableAmount(before);
        flow.setAfterAvailableAmount(after);
        flow.setRelatedVoucherId(relatedVoucherId);
        flow.setOperatedBy(operatedBy);
        flow.setOperatedAt(Instant.now());
        flowRepository.save(flow);
    }

    private static BigDecimal parsePositiveAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("VALID_400", "金额不能为空", 400);
        }
        try {
            BigDecimal amount = new BigDecimal(raw);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("VALID_400", "金额必须大于 0", 400);
            }
            return amount;
        } catch (NumberFormatException ex) {
            throw new BusinessException("VALID_400", "金额格式非法", 400);
        }
    }

    private static Map<String, Object> auditMap(DvVoucher voucher) {
        return Map.of(
                "voucher_no", voucher.getVoucherNo(),
                "holder_id", voucher.getHolderId(),
                "available_amount", voucher.getAvailableAmount().toPlainString(),
                "voucher_status", voucher.getVoucherStatus());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
