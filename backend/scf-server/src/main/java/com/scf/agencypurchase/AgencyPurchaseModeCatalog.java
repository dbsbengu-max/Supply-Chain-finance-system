package com.scf.agencypurchase;

import com.scf.common.exception.BusinessException;

import java.util.List;
import java.util.Optional;

public final class AgencyPurchaseModeCatalog {

    private AgencyPurchaseModeCatalog() {
    }

    public record ModeDefinition(
            String modeKey,
            String orderMode,
            String fundSource,
            String pickupType,
            String label
    ) {
    }

    public record DictItem(String code, String label) {
    }

    private static final List<ModeDefinition> VALID_MODES = List.of(
            new ModeDefinition("SO_SF_PP", "STOCK_ORDER", "SELF_FUNDED", "PAYMENT_PICKUP", "订单模式-自有资金-打款提货"),
            new ModeDefinition("SO_SF_PR", "STOCK_ORDER", "SELF_FUNDED", "PAYMENT_REDEEM", "订单模式-自有资金-打款赎单"),
            new ModeDefinition("SO_SF_OP", "STOCK_ORDER", "SELF_FUNDED", "ORDER_PICKUP", "订单模式-自有资金-订单提货"),
            new ModeDefinition("SO_TF_PP", "STOCK_ORDER", "THIRD_PARTY_FUNDED", "PAYMENT_PICKUP", "订单模式-非自有-打款提货"),
            new ModeDefinition("SO_TF_PR", "STOCK_ORDER", "THIRD_PARTY_FUNDED", "PAYMENT_REDEEM", "订单模式-非自有-打款赎单"),
            new ModeDefinition("SO_TF_OP", "STOCK_ORDER", "THIRD_PARTY_FUNDED", "ORDER_PICKUP", "订单模式-非自有-订单提货"),
            new ModeDefinition("SP_SF_PP", "STOCK_PREPARE", "SELF_FUNDED", "PAYMENT_PICKUP", "备货模式-自有资金-打款提货"),
            new ModeDefinition("SP_TF_PR", "STOCK_PREPARE", "THIRD_PARTY_FUNDED", "PAYMENT_REDEEM", "备货模式-非自有-打款赎单")
    );

    public static List<ModeDefinition> allModes() {
        return VALID_MODES;
    }

    public static List<DictItem> orderModes() {
        return List.of(
                new DictItem("STOCK_ORDER", "订单模式"),
                new DictItem("STOCK_PREPARE", "备货模式"));
    }

    public static List<DictItem> fundSources() {
        return List.of(
                new DictItem("SELF_FUNDED", "自有资金"),
                new DictItem("THIRD_PARTY_FUNDED", "非自有资金"));
    }

    public static List<DictItem> pickupTypes() {
        return List.of(
                new DictItem("PAYMENT_PICKUP", "打款提货"),
                new DictItem("PAYMENT_REDEEM", "打款赎单"),
                new DictItem("ORDER_PICKUP", "订单提货"));
    }

    public static List<DictItem> applicationStatuses() {
        return List.of(
                new DictItem("DRAFT", "草稿"),
                new DictItem("SUBMITTED", "已提交"),
                new DictItem("REVIEWING", "审核中"),
                new DictItem("APPROVED", "已通过"),
                new DictItem("REJECTED", "已驳回"),
                new DictItem("CANCELLED", "已取消"));
    }

    public static List<DictItem> sagaStatuses() {
        return List.of(
                new DictItem("RUNNING", "执行中"),
                new DictItem("SUCCESS", "成功"),
                new DictItem("FAILED", "失败"));
    }

    public static ModeDefinition resolve(String orderMode, String fundSource, String pickupType) {
        return find(orderMode, fundSource, pickupType)
                .orElseThrow(() -> new BusinessException(
                        "VALID_400",
                        "无效的代采模式组合: " + orderMode + "/" + fundSource + "/" + pickupType,
                        400));
    }

    public static Optional<ModeDefinition> find(String orderMode, String fundSource, String pickupType) {
        return VALID_MODES.stream()
                .filter(m -> m.orderMode().equals(orderMode)
                        && m.fundSource().equals(fundSource)
                        && m.pickupType().equals(pickupType))
                .findFirst();
    }
}
