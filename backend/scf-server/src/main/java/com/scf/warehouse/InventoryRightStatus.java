package com.scf.warehouse;

import java.util.Map;
import java.util.Set;

public final class InventoryRightStatus {

    public static final String IN_STOCK = "IN_STOCK";
    public static final String FROZEN = "FROZEN";
    public static final String PLEDGED = "PLEDGED";
    public static final String RELEASE_REVIEW = "RELEASE_REVIEW";
    public static final String RELEASED = "RELEASED";
    public static final String PENDING_OUT = "PENDING_OUT";
    public static final String OUT_STOCK = "OUT_STOCK";
    public static final String INVENTORY_EXCEPTION = "INVENTORY_EXCEPTION";

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            IN_STOCK, Set.of(FROZEN, PENDING_OUT, INVENTORY_EXCEPTION),
            FROZEN, Set.of(PLEDGED, IN_STOCK, INVENTORY_EXCEPTION),
            PLEDGED, Set.of(RELEASE_REVIEW, INVENTORY_EXCEPTION),
            RELEASE_REVIEW, Set.of(RELEASED, PLEDGED),
            RELEASED, Set.of(FROZEN, PENDING_OUT, IN_STOCK),
            PENDING_OUT, Set.of(OUT_STOCK, IN_STOCK, RELEASED),
            OUT_STOCK, Set.of(),
            INVENTORY_EXCEPTION, Set.of(IN_STOCK));

    private InventoryRightStatus() {
    }

    public static void assertTransition(String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        Set<String> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new IllegalStateException("不允许从 " + from + " 流转到 " + to);
        }
    }

    public static String label(String code) {
        return switch (code) {
            case IN_STOCK -> "在库";
            case FROZEN -> "已冻结";
            case PLEDGED -> "已质押";
            case RELEASE_REVIEW -> "解押审核中";
            case RELEASED -> "已解押";
            case PENDING_OUT -> "待出库";
            case OUT_STOCK -> "已出库";
            case INVENTORY_EXCEPTION -> "盘库异常";
            default -> code;
        };
    }
}
