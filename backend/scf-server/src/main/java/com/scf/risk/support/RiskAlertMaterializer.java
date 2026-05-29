package com.scf.risk.support;

import com.scf.bi.support.BiMetricsDao;
import com.scf.bi.support.BiMetricsDao.CountAmount;
import com.scf.bi.support.BiMetricsDao.InventoryTotals;
import com.scf.bi.support.BiMetricsDao.RiskRow;
import com.scf.bi.support.BiQueryScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class RiskAlertMaterializer {

    private static final int MATERIALIZE_LIMIT = 500;
    private static final String DEFAULT_CURRENCY = "CNY";

    private final BiMetricsDao metricsDao;

    public RiskAlertMaterializer(BiMetricsDao metricsDao) {
        this.metricsDao = metricsDao;
    }

    public List<MaterializedRiskAlert> materialize(BiQueryScope scope) {
        List<MaterializedRiskAlert> alerts = new ArrayList<>();

        for (RiskRow row : metricsDao.overdueFinances(scope, MATERIALIZE_LIMIT)) {
            alerts.add(new MaterializedRiskAlert(
                    alertKey("FINANCE_OVERDUE", row.relatedType(), row.id()),
                    "FINANCE_OVERDUE",
                    "HIGH",
                    "融资逾期",
                    "融资单 " + row.label() + " 已逾期",
                    row.id(),
                    row.relatedType(),
                    row.label(),
                    row.amount(),
                    DEFAULT_CURRENCY));
        }
        for (RiskRow row : metricsDao.unmatchedFlowSamples(scope, MATERIALIZE_LIMIT)) {
            alerts.add(new MaterializedRiskAlert(
                    alertKey("BANK_FLOW_UNMATCHED", row.relatedType(), row.id()),
                    "BANK_FLOW_UNMATCHED",
                    "MEDIUM",
                    "未匹配入账流水",
                    "流水 " + row.label() + " 尚未匹配融资",
                    row.id(),
                    row.relatedType(),
                    row.label(),
                    row.amount(),
                    DEFAULT_CURRENCY));
        }
        for (RiskRow row : metricsDao.abnormalPrices(MATERIALIZE_LIMIT)) {
            alerts.add(new MaterializedRiskAlert(
                    alertKey("PRICE_ABNORMAL", row.relatedType(), row.id()),
                    "PRICE_ABNORMAL",
                    "MEDIUM",
                    "价格异常",
                    "SKU " + row.label() + " 价格记录异常",
                    row.id(),
                    row.relatedType(),
                    row.label(),
                    row.amount(),
                    DEFAULT_CURRENCY));
        }
        for (RiskRow row : metricsDao.inventoryExceptions(scope, MATERIALIZE_LIMIT)) {
            alerts.add(new MaterializedRiskAlert(
                    alertKey("INVENTORY_STOCKTAKE", row.relatedType(), row.id()),
                    "INVENTORY_STOCKTAKE",
                    "LOW",
                    "库存盘点异常",
                    "批次 " + row.label() + " 存在盘点差异",
                    row.id(),
                    row.relatedType(),
                    row.label(),
                    row.amount(),
                    DEFAULT_CURRENCY));
        }
        return alerts;
    }

    public long activeAlertCount(BiQueryScope scope) {
        CountAmount unmatched = metricsDao.unmatchedInflows(scope);
        InventoryTotals inventory = metricsDao.inventoryTotals(scope);
        return metricsDao.countOverdueFinances(scope)
                + unmatched.count()
                + metricsDao.countAbnormalPrices()
                + inventory.stocktakeExceptionCount();
    }

    public static String alertKey(String code, String relatedType, String relatedId) {
        return code + "|" + relatedType + "|" + relatedId;
    }

    public record MaterializedRiskAlert(
            String alertKey,
            String alertCode,
            String severity,
            String title,
            String message,
            String relatedId,
            String relatedType,
            String relatedLabel,
            BigDecimal amount,
            String currency) {

        public String amountPlain() {
            if (amount == null) {
                return null;
            }
            return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
