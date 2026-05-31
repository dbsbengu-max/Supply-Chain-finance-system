package com.scf.saga.support;

public final class SagaBusinessRouteResolver {

    private SagaBusinessRouteResolver() {
    }

    public static String resolve(String businessType, String businessId) {
        if (businessType == null || businessId == null || businessId.isBlank()) {
            return null;
        }
        return switch (businessType) {
            case "AGENCY_PURCHASE" -> "/agency-purchase/applications/" + businessId;
            case "VOUCHER" -> "/vouchers/" + businessId;
            case "FINANCE_APPLICATION" -> "/finance/applications";
            case "CLEARING_EXECUTION" -> "/accounts/clearing";
            case "TRADE_ORDER" -> "/trade/orders";
            default -> null;
        };
    }
}
