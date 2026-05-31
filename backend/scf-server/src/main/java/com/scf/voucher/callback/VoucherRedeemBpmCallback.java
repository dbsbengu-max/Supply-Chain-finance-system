package com.scf.voucher.callback;

import com.scf.bpm.callback.BusinessProcessCallback;
import com.scf.voucher.service.VoucherService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class VoucherRedeemBpmCallback implements BusinessProcessCallback {

    public static final String BUSINESS_TYPE = "VOUCHER_REDEEM";

    private final ObjectProvider<VoucherService> voucherServiceProvider;

    public VoucherRedeemBpmCallback(ObjectProvider<VoucherService> voucherServiceProvider) {
        this.voucherServiceProvider = voucherServiceProvider;
    }

    @Override
    public boolean supports(String businessType) {
        return BUSINESS_TYPE.equals(businessType);
    }

    @Override
    public void beforeApprove(String businessType, String businessId, String nodeCode) {
        voucherService().assertRedeemPendingForBpm(businessId);
    }

    @Override
    public void onProcessApproved(String businessType, String businessId) {
        voucherService().onRedeemBpmApproved(businessId);
    }

    @Override
    public void onProcessRejected(String businessType, String businessId) {
        voucherService().onRedeemBpmRejected(businessId);
    }

    private VoucherService voucherService() {
        return voucherServiceProvider.getObject();
    }
}
