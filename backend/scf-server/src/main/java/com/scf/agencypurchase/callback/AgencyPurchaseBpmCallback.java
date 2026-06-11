package com.scf.agencypurchase.callback;

import com.scf.agencypurchase.service.AgencyPurchaseApplicationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.scf.bpm.callback.BusinessProcessCallback;

@Component
public class AgencyPurchaseBpmCallback implements BusinessProcessCallback {

    public static final String BUSINESS_TYPE = "AGENCY_PURCHASE";

    private final ObjectProvider<AgencyPurchaseApplicationService> applicationServiceProvider;

    public AgencyPurchaseBpmCallback(ObjectProvider<AgencyPurchaseApplicationService> applicationServiceProvider) {
        this.applicationServiceProvider = applicationServiceProvider;
    }

    @Override
    public boolean supports(String businessType) {
        return BUSINESS_TYPE.equals(businessType);
    }

    @Override
    public void beforeApprove(String businessType, String businessId, String nodeCode) {
        applicationService().assertReviewingForBpm(businessId);
    }

    @Override
    public void onProcessApproved(String businessType, String businessId) {
        applicationService().onBpmApproved(businessId);
    }

    @Override
    public void onProcessRejected(String businessType, String businessId) {
        applicationService().onBpmRejected(businessId);
    }

    private AgencyPurchaseApplicationService applicationService() {
        return applicationServiceProvider.getObject();
    }
}
