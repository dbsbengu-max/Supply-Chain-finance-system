package com.scf.account.service;

import com.scf.account.dto.AccountBalanceSummaryView;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.finance.entity.AcctVirtualAccount;
import com.scf.finance.repository.AcctVirtualAccountRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountSummaryService {

    private final AcctVirtualAccountRepository accountRepository;
    private final TenantContext tenantContext;
    private final DataScopeHelper dataScopeHelper;

    public AccountSummaryService(
            AcctVirtualAccountRepository accountRepository,
            TenantContext tenantContext,
            DataScopeHelper dataScopeHelper) {
        this.accountRepository = accountRepository;
        this.tenantContext = tenantContext;
        this.dataScopeHelper = dataScopeHelper;
    }

    public List<AccountBalanceSummaryView> balanceSummary() {
        tenantContext.requirePermission("ACCOUNT_FLOW_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        List<AcctVirtualAccount> accounts = accountRepository.findSummaryAccounts(
                operatorId, projectId, resolveFundingScopeId());
        return accounts.stream().map(this::toView).toList();
    }

    private String resolveFundingScopeId() {
        UserContext user = SecurityUtils.currentUser();
        if (dataScopeHelper.isPlatformRole(user) || dataScopeHelper.canReadOperatorData(user)) {
            return null;
        }
        if (dataScopeHelper.isFundingRole(user)) {
            return user.enterpriseId();
        }
        return user != null ? user.enterpriseId() : null;
    }

    private AccountBalanceSummaryView toView(AcctVirtualAccount account) {
        BigDecimal balance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
        BigDecimal frozen = account.getFrozenBalance() != null ? account.getFrozenBalance() : BigDecimal.ZERO;
        BigDecimal available = balance.subtract(frozen);
        return new AccountBalanceSummaryView(
                account.getId(),
                account.getAccountType(),
                account.getAccountNo(),
                account.getAccountName(),
                account.getCurrency(),
                balance.toPlainString(),
                frozen.toPlainString(),
                available.toPlainString(),
                account.getEnterpriseId(),
                account.getFundingPartyId());
    }
}
