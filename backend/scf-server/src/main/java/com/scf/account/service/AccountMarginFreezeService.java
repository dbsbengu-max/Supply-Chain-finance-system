package com.scf.account.service;

import com.scf.audit.service.AuditLogService;
import com.scf.common.exception.BusinessException;
import com.scf.finance.entity.AcctVirtualAccount;
import com.scf.finance.repository.AcctVirtualAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class AccountMarginFreezeService {

    private final AcctVirtualAccountRepository accountRepository;
    private final AuditLogService auditLogService;

    public AccountMarginFreezeService(
            AcctVirtualAccountRepository accountRepository,
            AuditLogService auditLogService) {
        this.accountRepository = accountRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public BigDecimal freezeMargin(
            String accountId,
            BigDecimal amount,
            String businessType,
            String businessId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("VALID_400", "冻结金额必须大于 0", 400);
        }
        AcctVirtualAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new BusinessException("DATA_404", "保证金账户不存在", 404));
        BigDecimal available = account.getBalance().subtract(account.getFrozenBalance());
        if (available.compareTo(amount) < 0) {
            throw new BusinessException("ACCOUNT_400", "保证金可用余额不足", 400);
        }
        Map<String, Object> before = snapshot(account);
        account.setFrozenBalance(account.getFrozenBalance().add(amount));
        accountRepository.save(account);
        auditLogService.logAsSystem(
                "system", account.getOperatorId(), account.getProjectId(), null,
                "ACCOUNT_MARGIN_FREEZE", businessType, businessId, before, snapshot(account));
        return amount;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void unfreezeMargin(
            String accountId,
            BigDecimal amount,
            String businessType,
            String businessId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        AcctVirtualAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new BusinessException("DATA_404", "保证金账户不存在", 404));
        Map<String, Object> before = snapshot(account);
        BigDecimal newFrozen = account.getFrozenBalance().subtract(amount);
        if (newFrozen.compareTo(BigDecimal.ZERO) < 0) {
            newFrozen = BigDecimal.ZERO;
        }
        account.setFrozenBalance(newFrozen);
        accountRepository.save(account);
        auditLogService.logAsSystem(
                "system", account.getOperatorId(), account.getProjectId(), null,
                "ACCOUNT_MARGIN_UNFREEZE", businessType, businessId, before, snapshot(account));
    }

    private static Map<String, Object> snapshot(AcctVirtualAccount account) {
        return Map.of(
                "account_id", account.getId(),
                "balance", account.getBalance(),
                "frozen_balance", account.getFrozenBalance());
    }
}
