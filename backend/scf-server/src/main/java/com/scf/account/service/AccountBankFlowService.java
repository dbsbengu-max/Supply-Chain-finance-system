package com.scf.account.service;

import com.scf.account.dto.BankFlowImportRequest;
import com.scf.account.dto.BankFlowMatchRequest;
import com.scf.account.dto.BankFlowMatchView;
import com.scf.account.dto.BankFlowView;
import com.scf.audit.service.AuditLogService;
import com.scf.common.dto.PageResponse;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.finance.entity.AcctBankFlow;
import com.scf.finance.entity.AcctVirtualAccount;
import com.scf.finance.entity.FnFinanceApplication;
import com.scf.finance.repository.AcctBankFlowRepository;
import com.scf.finance.repository.AcctVirtualAccountRepository;
import com.scf.finance.repository.FnFinanceApplicationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AccountBankFlowService {

    public static final String MATCH_UNMATCHED = "UNMATCHED";
    public static final String MATCH_MATCHED = "MATCHED";
    public static final String SOURCE_FINANCE = "FINANCE";
    private static final Set<String> REPAYABLE_STATUSES = Set.of("DISBURSED", "REPAYING", "OVERDUE");

    private final AcctBankFlowRepository bankFlowRepository;
    private final AcctVirtualAccountRepository accountRepository;
    private final FnFinanceApplicationRepository financeRepository;
    private final TenantContext tenantContext;
    private final DataScopeHelper dataScopeHelper;
    private final AuditLogService auditLogService;

    public AccountBankFlowService(
            AcctBankFlowRepository bankFlowRepository,
            AcctVirtualAccountRepository accountRepository,
            FnFinanceApplicationRepository financeRepository,
            TenantContext tenantContext,
            DataScopeHelper dataScopeHelper,
            AuditLogService auditLogService) {
        this.bankFlowRepository = bankFlowRepository;
        this.accountRepository = accountRepository;
        this.financeRepository = financeRepository;
        this.tenantContext = tenantContext;
        this.dataScopeHelper = dataScopeHelper;
        this.auditLogService = auditLogService;
    }

    public PageResponse<BankFlowView> list(
            int pageNo, int pageSize, String matchStatus, String flowType, String accountId) {
        tenantContext.requirePermission("ACCOUNT_FLOW_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        PageRequest pageable = PageRequest.of(Math.max(pageNo - 1, 0), Math.max(pageSize, 1));
        Page<AcctBankFlow> page = bankFlowRepository.findScoped(
                operatorId, projectId, blankToNull(matchStatus), blankToNull(flowType), blankToNull(accountId), pageable);
        List<BankFlowView> records = page.getContent().stream().map(this::toView).toList();
        return PageResponse.of(pageNo, pageSize, page.getTotalElements(), records);
    }

    @Transactional
    public List<BankFlowView> importFlows(BankFlowImportRequest request) {
        tenantContext.requirePermission("ACCOUNT_FLOW_IMPORT");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        List<BankFlowView> imported = new ArrayList<>();
        for (BankFlowImportRequest.BankFlowImportItem item : request.flows()) {
            imported.add(importOne(operatorId, projectId, item));
        }
        return imported;
    }

    @Transactional
    public BankFlowMatchView match(String flowId, BankFlowMatchRequest request) {
        tenantContext.requirePermission("ACCOUNT_FLOW_MATCH");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();

        AcctBankFlow flow = loadScopedFlow(flowId, operatorId, projectId);
        if (!"IN".equals(flow.getFlowType())) {
            throw new BusinessException("VALID_400", "仅支持匹配 IN 类型流水", 400);
        }
        if (!MATCH_UNMATCHED.equals(flow.getMatchStatus())) {
            throw new BusinessException("STATE_409", "流水已匹配或不可匹配", 409);
        }

        FnFinanceApplication finance = loadAccessibleFinance(request.financeId(), operatorId, projectId);
        if (!REPAYABLE_STATUSES.contains(finance.getFinanceStatus())) {
            throw new BusinessException("STATE_409", "融资单状态不可匹配还款流水", 409);
        }
        if (!finance.getCurrency().equals(flow.getCurrency())) {
            throw new BusinessException("VALID_400", "流水币种与融资申请不一致", 400);
        }

        flow.setMatchStatus(MATCH_MATCHED);
        flow.setSourceType(SOURCE_FINANCE);
        flow.setSourceId(finance.getId());
        bankFlowRepository.save(flow);

        auditLogService.log(
                "ACCOUNT_FLOW_MATCH",
                "BANK_FLOW",
                flow.getId(),
                Map.of("match_status", MATCH_UNMATCHED),
                Map.of("match_status", MATCH_MATCHED, "finance_id", finance.getId()));

        return new BankFlowMatchView(flow.getId(), finance.getId(), flow.getMatchStatus());
    }

    BankFlowView toView(AcctBankFlow flow) {
        return new BankFlowView(
                flow.getId(),
                flow.getAccountId(),
                flow.getExternalFlowNo(),
                flow.getFlowType(),
                flow.getAmount().toPlainString(),
                flow.getCurrency(),
                flow.getCounterpartyName(),
                flow.getCounterpartyAccount(),
                flow.getFlowTime(),
                flow.getMatchStatus(),
                flow.getSourceType(),
                flow.getSourceId());
    }

    AcctBankFlow loadScopedFlow(String flowId, String operatorId, String projectId) {
        AcctBankFlow flow = bankFlowRepository.findById(flowId)
                .orElseThrow(() -> new BusinessException("DATA_404", "银行流水不存在", 404));
        accountRepository.findScopedById(flow.getAccountId(), operatorId, projectId)
                .orElseThrow(() -> new BusinessException("AUTH_403", "无权访问该银行流水", 403));
        return flow;
    }

    private BankFlowView importOne(
            String operatorId, String projectId, BankFlowImportRequest.BankFlowImportItem item) {
        AcctVirtualAccount account = accountRepository
                .findScopedById(item.accountId(), operatorId, projectId)
                .orElseThrow(() -> new BusinessException("DATA_404", "账户不存在", 404));
        if (!"REPAYMENT".equals(account.getAccountType())) {
            throw new BusinessException("VALID_400", "仅支持向 REPAYMENT 账户导入 IN 流水", 400);
        }
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new BusinessException("ACCOUNT_409", "账户不可用", 409);
        }
        if (bankFlowRepository.existsByAccountIdAndExternalFlowNo(account.getId(), item.externalFlowNo())) {
            throw new BusinessException("DATA_409", "external_flow_no 已存在", 409);
        }

        BigDecimal amount = parseAmount(item.amount());
        if (!account.getCurrency().equals(item.currency())) {
            throw new BusinessException("VALID_400", "流水币种与账户不一致", 400);
        }

        Instant flowTime = parseFlowTime(item.flowTime());
        AcctBankFlow flow = new AcctBankFlow();
        flow.setId(IdGenerator.nextId());
        flow.setAccountId(account.getId());
        flow.setExternalFlowNo(item.externalFlowNo());
        flow.setFlowType("IN");
        flow.setAmount(amount);
        flow.setCurrency(item.currency());
        flow.setCounterpartyName(item.counterpartyName());
        flow.setCounterpartyAccount(item.counterpartyAccount());
        flow.setFlowTime(flowTime);
        flow.setMatchStatus(MATCH_UNMATCHED);
        bankFlowRepository.save(flow);

        AcctVirtualAccount locked = accountRepository.findByIdForUpdate(account.getId()).orElseThrow();
        locked.setBalance(locked.getBalance().add(amount));
        accountRepository.save(locked);

        auditLogService.log(
                "ACCOUNT_FLOW_IMPORT",
                "BANK_FLOW",
                flow.getId(),
                null,
                Map.of("account_id", account.getId(), "amount", amount.toPlainString(), "match_status", MATCH_UNMATCHED));

        return toView(flow);
    }

    private FnFinanceApplication loadAccessibleFinance(String financeId, String operatorId, String projectId) {
        FnFinanceApplication finance = financeRepository
                .findByIdAndOperatorIdAndProjectIdAndDeletedFlag(financeId, operatorId, projectId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "融资申请不存在", 404));
        UserContext user = SecurityUtils.currentUser();
        if (!dataScopeHelper.canAccessFinance(user, finance.getCustomerId(), finance.getFundingPartyId())) {
            throw new BusinessException("AUTH_403", "无权访问该融资申请", 403);
        }
        return finance;
    }

    private static BigDecimal parseAmount(String raw) {
        try {
            BigDecimal amount = new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("VALID_400", "amount 必须大于 0", 400);
            }
            return amount;
        } catch (NumberFormatException ex) {
            throw new BusinessException("VALID_400", "amount 格式非法", 400);
        }
    }

    private static Instant parseFlowTime(String raw) {
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("VALID_400", "flow_time 格式非法，需 ISO-8601", 400);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
