package com.scf.saga.service;

import com.scf.saga.entity.BizEventOutbox;
import com.scf.voucher.service.VoucherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SagaEventHandler {

    private static final Logger log = LoggerFactory.getLogger(SagaEventHandler.class);

    private final VoucherService voucherService;

    public SagaEventHandler(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    public void handle(BizEventOutbox event) {
        log.info("Handling saga event type={} businessType={} businessId={}",
                event.getEventType(), event.getBusinessType(), event.getBusinessId());
        if ("FINANCE_DISBURSED".equals(event.getEventType())) {
            voucherService.handleFinanceDisbursed(event);
            return;
        }
        if ("REPAYMENT_SETTLED".equals(event.getEventType())) {
            voucherService.handleRepaymentSettled(event);
            return;
        }
        log.debug("No handler registered for event type {}", event.getEventType());
    }
}
