package com.scf.bi.service;

import com.scf.bi.dto.BiDashboardDtos;
import com.scf.bi.dto.BiDashboardDtos.*;
import com.scf.bi.support.BiMetricsDao;
import com.scf.bi.support.BiMetricsDao.*;
import com.scf.bi.support.BiQueryScope;
import com.scf.bi.support.BiScopeResolver;
import com.scf.risk.support.RiskAlertMaterializer;
import com.scf.risk.support.RiskAlertMaterializer.MaterializedRiskAlert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
public class BiDashboardService {

    private static final String DEFAULT_CURRENCY = "CNY";
    private static final int DEFAULT_TREND_MONTHS = 6;

    private final BiScopeResolver scopeResolver;
    private final BiMetricsDao metricsDao;
    private final RiskAlertMaterializer riskAlertMaterializer;

    public BiDashboardService(
            BiScopeResolver scopeResolver,
            BiMetricsDao metricsDao,
            RiskAlertMaterializer riskAlertMaterializer) {
        this.scopeResolver = scopeResolver;
        this.metricsDao = metricsDao;
        this.riskAlertMaterializer = riskAlertMaterializer;
    }

    @Transactional(readOnly = true)
    public BiOverviewView overview() {
        BiQueryScope scope = scopeResolver.requireScope();
        CountAmount orders = metricsDao.orderTotals(scope);
        CountAmount finance = metricsDao.financeTotals(scope);
        CountAmount outstanding = metricsDao.outstandingFinance(scope);
        ClearingTotals clearing = metricsDao.clearingTotals(scope);
        CountAmount repayment = metricsDao.repaymentTotals(scope);
        InventoryTotals inventory = metricsDao.inventoryTotals(scope);
        CountAmount voucher = metricsDao.voucherFinance(scope);
        long riskCount = riskAlertCount(scope);

        return new BiOverviewView(
                Instant.now(),
                scope.operatorId(),
                scope.projectId(),
                DEFAULT_CURRENCY,
                orders.count(),
                money(orders.amount()),
                finance.count(),
                outstanding.count(),
                money(outstanding.amount()),
                money(finance.amount()),
                clearing.executedCount(),
                money(repayment.amount()),
                inventory.lotCount(),
                money(inventory.valuationTotal()),
                voucher.count(),
                riskCount);
    }

    @Transactional(readOnly = true)
    public BiTradeTrendView tradeTrend(Integer months) {
        BiQueryScope scope = scopeResolver.requireScope();
        int window = normalizeMonths(months);
        List<BiTrendPoint> points = metricsDao.orderTrend(scope, window).stream()
                .map(row -> new BiTrendPoint(
                        row.period(),
                        row.count(),
                        money(row.amount()),
                        DEFAULT_CURRENCY))
                .toList();
        return new BiTradeTrendView(DEFAULT_CURRENCY, window, points);
    }

    @Transactional(readOnly = true)
    public BiFinanceSummaryView financeSummary() {
        BiQueryScope scope = scopeResolver.requireScope();
        CountAmount finance = metricsDao.financeTotals(scope);
        List<BiStatusBucket> byStatus = metricsDao.financeByStatus(scope).stream()
                .map(this::toStatusBucket)
                .toList();
        CountAmount disburseSuccess = metricsDao.disbursementSuccess(scope);
        CountAmount voucher = metricsDao.voucherFinance(scope);

        return new BiFinanceSummaryView(
                DEFAULT_CURRENCY,
                finance.count(),
                byStatus,
                metricsDao.disbursementCount(scope),
                disburseSuccess.count(),
                money(disburseSuccess.amount()),
                voucher.count(),
                money(voucher.amount()));
    }

    @Transactional(readOnly = true)
    public BiWarehouseSummaryView warehouseSummary() {
        BiQueryScope scope = scopeResolver.requireScope();
        InventoryTotals inventory = metricsDao.inventoryTotals(scope);
        List<BiStatusBucket> byRightStatus = metricsDao.inventoryByRightStatus(scope).stream()
                .map(this::toStatusBucket)
                .toList();

        return new BiWarehouseSummaryView(
                DEFAULT_CURRENCY,
                inventory.lotCount(),
                qty(inventory.quantityTotal()),
                qty(inventory.availableTotal()),
                qty(inventory.pledgedTotal()),
                money(inventory.valuationTotal()),
                byRightStatus,
                inventory.stocktakeExceptionCount());
    }

    @Transactional(readOnly = true)
    public BiClearingSummaryView clearingSummary() {
        BiQueryScope scope = scopeResolver.requireScope();
        ClearingTotals clearing = metricsDao.clearingTotals(scope);
        CountAmount repayment = metricsDao.repaymentTotals(scope);
        CountAmount unmatched = metricsDao.unmatchedInflows(scope);

        return new BiClearingSummaryView(
                DEFAULT_CURRENCY,
                clearing.executedCount(),
                money(clearing.principal()),
                money(clearing.interest()),
                money(clearing.feeTotal()),
                repayment.count(),
                unmatched.count(),
                money(unmatched.amount()));
    }

    @Transactional(readOnly = true)
    public BiRiskAlertsView riskAlerts() {
        BiQueryScope scope = scopeResolver.requireScope();
        List<BiRiskAlertItem> alerts = riskAlertMaterializer.materialize(scope).stream()
                .map(this::toRiskItem)
                .toList();
        return new BiRiskAlertsView(riskAlertMaterializer.activeAlertCount(scope), alerts);
    }

    private BiRiskAlertItem toRiskItem(MaterializedRiskAlert alert) {
        return new BiRiskAlertItem(
                alert.alertCode(),
                alert.severity(),
                alert.title(),
                alert.message(),
                alert.relatedId(),
                alert.relatedType());
    }

    private long riskAlertCount(BiQueryScope scope) {
        return riskAlertMaterializer.activeAlertCount(scope);
    }

    private BiStatusBucket toStatusBucket(StatusRow row) {
        return new BiStatusBucket(row.status(), row.count(), money(row.amount()), DEFAULT_CURRENCY);
    }

    private static int normalizeMonths(Integer months) {
        if (months == null || months < 1) {
            return DEFAULT_TREND_MONTHS;
        }
        return Math.min(months, 24);
    }

    private static String money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String qty(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP).toPlainString();
        }
        return value.setScale(6, RoundingMode.HALF_UP).toPlainString();
    }
}
