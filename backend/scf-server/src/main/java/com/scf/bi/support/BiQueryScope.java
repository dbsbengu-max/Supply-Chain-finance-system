package com.scf.bi.support;

/**
 * Resolved read scope for BI aggregations (operator/project + optional enterprise filters).
 */
public record BiQueryScope(
        String operatorId,
        String projectId,
        String orderEnterpriseId,
        String financeCustomerId,
        String financeFundingPartyId,
        String inventoryOwnerId,
        String inventoryWarehouseCompanyId) {

    public static BiQueryScope operatorProject(String operatorId, String projectId) {
        return new BiQueryScope(operatorId, projectId, null, null, null, null, null);
    }
}
