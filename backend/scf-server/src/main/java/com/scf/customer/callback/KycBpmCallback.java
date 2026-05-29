package com.scf.customer.callback;

import com.scf.customer.service.EnterpriseService;
import com.scf.bpm.callback.BusinessProcessCallback;
import org.springframework.stereotype.Component;

@Component
public class KycBpmCallback implements BusinessProcessCallback {

    private final EnterpriseService enterpriseService;

    public KycBpmCallback(EnterpriseService enterpriseService) {
        this.enterpriseService = enterpriseService;
    }

    @Override
    public boolean supports(String businessType) {
        return "ENTERPRISE_KYC".equals(businessType);
    }

    @Override
    public void beforeApprove(String businessType, String businessId, String nodeCode) {
        // no-op: validation can be added here
    }

    @Override
    public void onProcessApproved(String businessType, String businessId) {
        enterpriseService.updateKycStatusFromBpm(businessId, "APPROVED");
    }

    @Override
    public void onProcessRejected(String businessType, String businessId) {
        enterpriseService.updateKycStatusFromBpm(businessId, "REJECTED");
    }
}
