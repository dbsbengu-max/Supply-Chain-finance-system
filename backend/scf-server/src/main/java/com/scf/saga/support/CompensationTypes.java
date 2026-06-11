package com.scf.saga.support;

import java.util.Set;

public final class CompensationTypes {

    public static final String ORDER_ROLLBACK = "ORDER_ROLLBACK";
    public static final String MARGIN_UNFREEZE = "MARGIN_UNFREEZE";
    public static final String INVENTORY_UNFREEZE = "INVENTORY_UNFREEZE";
    public static final String CONTRACT_SIGN_CALLBACK_REVIEW = "CONTRACT_SIGN_CALLBACK_REVIEW";

    private static final Set<String> HIGH_RISK = Set.of(ORDER_ROLLBACK);
    private static final Set<String> TERMINAL = Set.of("SUCCESS", "IGNORED", "CLOSED");

    private CompensationTypes() {
    }

    public static boolean isHighRisk(String compensationType) {
        return HIGH_RISK.contains(compensationType);
    }

    public static boolean isTerminalStatus(String status) {
        return TERMINAL.contains(status);
    }
}
