package com.scf.bpm.callback;

public interface BusinessProcessCallback {

    boolean supports(String businessType);

    void beforeApprove(String businessType, String businessId, String nodeCode);

    void onProcessApproved(String businessType, String businessId);

    void onProcessRejected(String businessType, String businessId);
}
