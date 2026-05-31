package com.scf.finance.service;

import com.scf.common.util.IdGenerator;
import com.scf.finance.entity.AcctBankFlow;
import com.scf.finance.repository.AcctBankFlowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class BankFlowService {

    public static final String SOURCE_DISBURSEMENT = "DISBURSEMENT";
    public static final String SOURCE_VOUCHER_REDEEM = "VOUCHER_REDEEM";
    public static final String MATCH_MATCHED = "MATCHED";

    private final AcctBankFlowRepository repository;

    public BankFlowService(AcctBankFlowRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void recordDisbursementPair(
            String disbursementId,
            String payerAccountId,
            String receiverAccountId,
            String externalFlowNo,
            BigDecimal amount,
            String currency,
            Instant flowTime,
            String counterpartyName,
            String counterpartyAccount) {
        recordFlow(
                payerAccountId,
                externalFlowNo + "-OUT",
                "OUT",
                amount,
                currency,
                flowTime,
                counterpartyName,
                counterpartyAccount,
                SOURCE_DISBURSEMENT,
                disbursementId);
        recordFlow(
                receiverAccountId,
                externalFlowNo + "-IN",
                "IN",
                amount,
                currency,
                flowTime,
                counterpartyName,
                counterpartyAccount,
                SOURCE_DISBURSEMENT,
                disbursementId);
    }

    @Transactional
    public void recordRedeemPair(
            String voucherId,
            String payerAccountId,
            String receiverAccountId,
            String externalFlowNo,
            BigDecimal amount,
            String currency,
            Instant flowTime,
            String counterpartyName,
            String counterpartyAccount) {
        recordFlow(
                payerAccountId,
                externalFlowNo + "-OUT",
                "OUT",
                amount,
                currency,
                flowTime,
                counterpartyName,
                counterpartyAccount,
                SOURCE_VOUCHER_REDEEM,
                voucherId);
        recordFlow(
                receiverAccountId,
                externalFlowNo + "-IN",
                "IN",
                amount,
                currency,
                flowTime,
                counterpartyName,
                counterpartyAccount,
                SOURCE_VOUCHER_REDEEM,
                voucherId);
    }

    private void recordFlow(
            String accountId,
            String externalFlowNo,
            String flowType,
            BigDecimal amount,
            String currency,
            Instant flowTime,
            String counterpartyName,
            String counterpartyAccount,
            String sourceType,
            String sourceId) {
        if (repository.existsByAccountIdAndExternalFlowNo(accountId, externalFlowNo)) {
            return;
        }
        AcctBankFlow flow = new AcctBankFlow();
        flow.setId(IdGenerator.nextId());
        flow.setAccountId(accountId);
        flow.setExternalFlowNo(externalFlowNo);
        flow.setFlowType(flowType);
        flow.setAmount(amount);
        flow.setCurrency(currency);
        flow.setCounterpartyName(counterpartyName);
        flow.setCounterpartyAccount(counterpartyAccount);
        flow.setFlowTime(flowTime);
        flow.setMatchStatus(MATCH_MATCHED);
        flow.setSourceType(sourceType);
        flow.setSourceId(sourceId);
        repository.save(flow);
    }
}
