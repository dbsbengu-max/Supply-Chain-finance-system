package com.scf.finance.service;

import com.scf.audit.service.AuditLogService;
import com.scf.common.exception.BusinessException;
import com.scf.finance.entity.AcctVirtualAccount;
import com.scf.finance.entity.FnDisbursement;
import com.scf.finance.entity.FnFinanceApplication;
import com.scf.finance.repository.AcctVirtualAccountRepository;
import com.scf.finance.repository.FnDisbursementRepository;
import com.scf.finance.repository.FnFinanceApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Service
public class DisburseCompletionService {

    private final FnFinanceApplicationRepository financeRepository;
    private final FnDisbursementRepository disbursementRepository;
    private final AcctVirtualAccountRepository accountRepository;
    private final BankFlowService bankFlowService;
    private final AuditLogService auditLogService;
    private final FinanceDisburseOutboxPublisher disburseOutboxPublisher;

    public DisburseCompletionService(
            FnFinanceApplicationRepository financeRepository,
            FnDisbursementRepository disbursementRepository,
            AcctVirtualAccountRepository accountRepository,
            BankFlowService bankFlowService,
            AuditLogService auditLogService,
            FinanceDisburseOutboxPublisher disburseOutboxPublisher) {
        this.financeRepository = financeRepository;
        this.disbursementRepository = disbursementRepository;
        this.accountRepository = accountRepository;
        this.bankFlowService = bankFlowService;
        this.auditLogService = auditLogService;
        this.disburseOutboxPublisher = disburseOutboxPublisher;
    }

    @Transactional
    public void completeSuccess(
            FnDisbursement disbursement,
            String externalFlowNo,
            Instant flowTime,
            String counterpartyName,
            String counterpartyAccount,
            String channelResponseId,
            String auditActorUserId,
            String auditOperatorId,
            String auditEnterpriseId) {
        if ("SUCCESS".equals(disbursement.getDisbursementStatus())) {
            return;
        }
        if (!"PENDING".equals(disbursement.getDisbursementStatus())) {
            throw new BusinessException("STATE_409", "放款单状态不可完成", 409);
        }

        FnFinanceApplication app = financeRepository.findByIdForUpdate(disbursement.getFinanceId())
                .orElseThrow(() -> new BusinessException("DATA_404", "融资申请不存在", 404));
        if (!"TO_DISBURSE".equals(app.getFinanceStatus())) {
            throw new BusinessException("STATE_409", "融资申请状态不可完成放款", 409);
        }

        AcctVirtualAccount payer = lockAccount(disbursement.getPayAccountId());
        AcctVirtualAccount receiver = lockAccount(disbursement.getReceiveAccountId());
        BigDecimal amount = disbursement.getAmount();

        if (payer.getBalance().compareTo(amount) < 0) {
            throw new BusinessException("ACCOUNT_409", "出款账户余额不足", 409);
        }

        Instant now = Instant.now();
        Instant effectiveFlowTime = flowTime == null ? now : flowTime;
        String flowNo = externalFlowNo == null || externalFlowNo.isBlank()
                ? "BANK-FLOW-" + disbursement.getId()
                : externalFlowNo;

        payer.setBalance(payer.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        accountRepository.save(payer);
        accountRepository.save(receiver);

        bankFlowService.recordDisbursementPair(
                disbursement.getId(),
                payer.getId(),
                receiver.getId(),
                flowNo,
                amount,
                disbursement.getCurrency(),
                effectiveFlowTime,
                counterpartyName,
                counterpartyAccount);

        BigDecimal disbursed = app.getDisbursedAmount() == null ? BigDecimal.ZERO : app.getDisbursedAmount();
        app.setDisbursedAmount(disbursed.add(amount));
        app.setFinanceStatus("DISBURSED");
        app.setUpdatedBy(auditActorUserId);
        app.setUpdatedAt(now);
        financeRepository.save(app);

        disbursement.setDisbursementStatus("SUCCESS");
        disbursement.setChannelResponseId(channelResponseId == null ? flowNo : channelResponseId);
        disbursementRepository.save(disbursement);

        auditLogService.logAsSystem(
                auditActorUserId,
                auditOperatorId,
                auditEnterpriseId,
                app.getProjectId(),
                "FINANCE_DISBURSE_CALLBACK",
                "FINANCE_APPLICATION",
                app.getId(),
                Map.of("finance_status", "TO_DISBURSE", "disbursement_status", "PENDING"),
                Map.of(
                        "disbursement_id", disbursement.getId(),
                        "external_flow_no", flowNo,
                        "finance_status", "DISBURSED",
                        "disbursement_status", "SUCCESS"));

        disburseOutboxPublisher.publishFinanceDisbursed(app, amount);
    }

    @Transactional
    public void markFailed(
            FnDisbursement disbursement,
            String channelResponseId,
            String auditActorUserId,
            String auditOperatorId,
            String auditEnterpriseId) {
        if ("FAILED".equals(disbursement.getDisbursementStatus())) {
            return;
        }
        if (!"PENDING".equals(disbursement.getDisbursementStatus())) {
            throw new BusinessException("STATE_409", "放款单状态不可标记失败", 409);
        }

        disbursement.setDisbursementStatus("FAILED");
        disbursement.setChannelResponseId(channelResponseId);
        disbursementRepository.save(disbursement);

        FnFinanceApplication app = financeRepository.findById(disbursement.getFinanceId()).orElse(null);
        String projectId = app == null ? null : app.getProjectId();

        auditLogService.logAsSystem(
                auditActorUserId,
                auditOperatorId,
                auditEnterpriseId,
                projectId,
                "FINANCE_DISBURSE_CALLBACK_FAILED",
                "FINANCE_APPLICATION",
                disbursement.getFinanceId(),
                Map.of("disbursement_status", "PENDING"),
                Map.of(
                        "disbursement_id", disbursement.getId(),
                        "disbursement_status", "FAILED",
                        "finance_status", "TO_DISBURSE"));
    }

    private AcctVirtualAccount lockAccount(String accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new BusinessException("ACCOUNT_409", "账户不存在或不可用", 409));
    }
}
