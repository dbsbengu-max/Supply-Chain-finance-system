package com.scf.bi.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Repository
public class BiMetricsDao {

    @PersistenceContext
    private EntityManager em;

    public CountAmount orderTotals(BiQueryScope scope) {
        Query q = em.createNativeQuery("""
                SELECT COUNT(*), COALESCE(SUM(total_amount), 0)
                FROM scf.tr_order
                WHERE operator_id = :operatorId AND project_id = :projectId AND deleted_flag = 0
                  AND (:orderEnterpriseId IS NULL
                       OR buyer_id = :orderEnterpriseId
                       OR seller_id = :orderEnterpriseId
                       OR trade_company_id = :orderEnterpriseId)
                """);
        bindScope(q, scope);
        q.setParameter("orderEnterpriseId", scope.orderEnterpriseId());
        return toCountAmount(q.getSingleResult());
    }

    public List<TrendRow> orderTrend(BiQueryScope scope, int months) {
        Instant since = Instant.now().minus(months * 31L, ChronoUnit.DAYS);
        Query q = em.createNativeQuery("""
                SELECT CONCAT(CAST(EXTRACT(YEAR FROM created_at) AS VARCHAR(4)), '-',
                       LPAD(CAST(EXTRACT(MONTH FROM created_at) AS VARCHAR(2)), 2, '0')) AS period,
                       COUNT(*), COALESCE(SUM(total_amount), 0)
                FROM scf.tr_order
                WHERE operator_id = :operatorId AND project_id = :projectId AND deleted_flag = 0
                  AND created_at >= :since
                  AND (:orderEnterpriseId IS NULL
                       OR buyer_id = :orderEnterpriseId
                       OR seller_id = :orderEnterpriseId
                       OR trade_company_id = :orderEnterpriseId)
                GROUP BY 1
                ORDER BY 1
                """);
        bindScope(q, scope);
        q.setParameter("orderEnterpriseId", scope.orderEnterpriseId());
        q.setParameter("since", since);
        return mapTrendRows(q.getResultList());
    }

    public CountAmount financeTotals(BiQueryScope scope) {
        Query q = em.createNativeQuery("""
                SELECT COUNT(*), COALESCE(SUM(disbursed_amount), 0)
                FROM scf.fn_finance_application
                WHERE operator_id = :operatorId AND project_id = :projectId AND deleted_flag = 0
                  AND (:financeCustomerId IS NULL OR customer_id = :financeCustomerId)
                  AND (:financeFundingPartyId IS NULL OR funding_party_id = :financeFundingPartyId)
                """);
        bindFinanceScope(q, scope);
        return toCountAmount(q.getSingleResult());
    }

    public CountAmount outstandingFinance(BiQueryScope scope) {
        Query q = em.createNativeQuery("""
                SELECT COUNT(*), COALESCE(SUM(disbursed_amount), 0)
                FROM scf.fn_finance_application
                WHERE operator_id = :operatorId AND project_id = :projectId AND deleted_flag = 0
                  AND finance_status IN ('DISBURSED', 'REPAYING', 'OVERDUE')
                  AND (:financeCustomerId IS NULL OR customer_id = :financeCustomerId)
                  AND (:financeFundingPartyId IS NULL OR funding_party_id = :financeFundingPartyId)
                """);
        bindFinanceScope(q, scope);
        return toCountAmount(q.getSingleResult());
    }

    public List<StatusRow> financeByStatus(BiQueryScope scope) {
        Query q = em.createNativeQuery("""
                SELECT finance_status, COUNT(*), COALESCE(SUM(disbursed_amount), 0)
                FROM scf.fn_finance_application
                WHERE operator_id = :operatorId AND project_id = :projectId AND deleted_flag = 0
                  AND (:financeCustomerId IS NULL OR customer_id = :financeCustomerId)
                  AND (:financeFundingPartyId IS NULL OR funding_party_id = :financeFundingPartyId)
                GROUP BY finance_status
                ORDER BY finance_status
                """);
        bindFinanceScope(q, scope);
        return mapStatusRows(q.getResultList());
    }

    public CountAmount voucherFinance(BiQueryScope scope) {
        Query q = em.createNativeQuery("""
                SELECT COUNT(*), COALESCE(SUM(disbursed_amount), 0)
                FROM scf.fn_finance_application
                WHERE operator_id = :operatorId AND project_id = :projectId AND deleted_flag = 0
                  AND source_type = 'VOUCHER'
                  AND (:financeCustomerId IS NULL OR customer_id = :financeCustomerId)
                  AND (:financeFundingPartyId IS NULL OR funding_party_id = :financeFundingPartyId)
                """);
        bindFinanceScope(q, scope);
        return toCountAmount(q.getSingleResult());
    }

    public CountAmount disbursementSuccess(BiQueryScope scope) {
        Query q = em.createNativeQuery("""
                SELECT COUNT(*), COALESCE(SUM(d.amount), 0)
                FROM scf.fn_disbursement d
                JOIN scf.fn_finance_application f ON f.id = d.finance_id
                WHERE f.operator_id = :operatorId AND f.project_id = :projectId AND f.deleted_flag = 0
                  AND d.disbursement_status = 'SUCCESS'
                  AND (:financeCustomerId IS NULL OR f.customer_id = :financeCustomerId)
                  AND (:financeFundingPartyId IS NULL OR f.funding_party_id = :financeFundingPartyId)
                """);
        bindFinanceScope(q, scope);
        return toCountAmount(q.getSingleResult());
    }

    public long disbursementCount(BiQueryScope scope) {
        Query q = em.createNativeQuery("""
                SELECT COUNT(*)
                FROM scf.fn_disbursement d
                JOIN scf.fn_finance_application f ON f.id = d.finance_id
                WHERE f.operator_id = :operatorId AND f.project_id = :projectId AND f.deleted_flag = 0
                  AND (:financeCustomerId IS NULL OR f.customer_id = :financeCustomerId)
                  AND (:financeFundingPartyId IS NULL OR f.funding_party_id = :financeFundingPartyId)
                """);
        bindFinanceScope(q, scope);
        return ((Number) q.getSingleResult()).longValue();
    }

    public InventoryTotals inventoryTotals(BiQueryScope scope) {
        Query q = em.createNativeQuery("""
                SELECT COUNT(*),
                       COALESCE(SUM(i.quantity), 0),
                       COALESCE(SUM(i.available_quantity), 0),
                       COALESCE(SUM(i.pledged_quantity), 0),
                       COALESCE(SUM(i.valuation_amount), 0),
                       COALESCE(SUM(CASE WHEN i.stocktake_exception = 1 THEN 1 ELSE 0 END), 0)
                FROM scf.wh_inventory i
                LEFT JOIN scf.wh_warehouse w ON w.id = i.warehouse_id
                WHERE i.deleted_flag = 0
                  AND i.operator_id = :operatorId AND i.project_id = :projectId
                  AND (:inventoryOwnerId IS NULL OR i.owner_id = :inventoryOwnerId)
                  AND (:inventoryWarehouseCompanyId IS NULL OR w.warehouse_company_id = :inventoryWarehouseCompanyId)
                """);
        bindInventoryScope(q, scope);
        Object[] row = (Object[]) q.getSingleResult();
        return new InventoryTotals(
                ((Number) row[0]).longValue(),
                toDecimal(row[1]),
                toDecimal(row[2]),
                toDecimal(row[3]),
                toDecimal(row[4]),
                ((Number) row[5]).longValue());
    }

    public List<StatusRow> inventoryByRightStatus(BiQueryScope scope) {
        Query q = em.createNativeQuery("""
                SELECT i.right_status, COUNT(*), COALESCE(SUM(i.valuation_amount), 0)
                FROM scf.wh_inventory i
                LEFT JOIN scf.wh_warehouse w ON w.id = i.warehouse_id
                WHERE i.deleted_flag = 0
                  AND i.operator_id = :operatorId AND i.project_id = :projectId
                  AND (:inventoryOwnerId IS NULL OR i.owner_id = :inventoryOwnerId)
                  AND (:inventoryWarehouseCompanyId IS NULL OR w.warehouse_company_id = :inventoryWarehouseCompanyId)
                GROUP BY i.right_status
                ORDER BY i.right_status
                """);
        bindInventoryScope(q, scope);
        return mapStatusRows(q.getResultList());
    }

    public ClearingTotals clearingTotals(BiQueryScope scope) {
        Query q = em.createNativeQuery("""
                SELECT COUNT(*),
                       COALESCE(SUM(cr.principal_amount), 0),
                       COALESCE(SUM(cr.interest_amount), 0),
                       COALESCE(SUM(cr.fee_amount + cr.penalty_amount), 0)
                FROM scf.clearing_result cr
                JOIN scf.fn_repayment r ON r.id = cr.repayment_id
                JOIN scf.fn_finance_application f ON f.id = r.finance_id
                WHERE cr.clearing_status = 'EXECUTED'
                  AND f.operator_id = :operatorId AND f.project_id = :projectId AND f.deleted_flag = 0
                  AND (:financeCustomerId IS NULL OR f.customer_id = :financeCustomerId)
                  AND (:financeFundingPartyId IS NULL OR f.funding_party_id = :financeFundingPartyId)
                """);
        bindFinanceScope(q, scope);
        Object[] row = (Object[]) q.getSingleResult();
        return new ClearingTotals(
                ((Number) row[0]).longValue(),
                toDecimal(row[1]),
                toDecimal(row[2]),
                toDecimal(row[3]));
    }

    public CountAmount repaymentTotals(BiQueryScope scope) {
        Query q = em.createNativeQuery("""
                SELECT COUNT(*), COALESCE(SUM(r.amount), 0)
                FROM scf.fn_repayment r
                JOIN scf.fn_finance_application f ON f.id = r.finance_id
                WHERE f.operator_id = :operatorId AND f.project_id = :projectId AND f.deleted_flag = 0
                  AND r.repayment_status = 'SUCCESS'
                  AND (:financeCustomerId IS NULL OR f.customer_id = :financeCustomerId)
                  AND (:financeFundingPartyId IS NULL OR f.funding_party_id = :financeFundingPartyId)
                """);
        bindFinanceScope(q, scope);
        return toCountAmount(q.getSingleResult());
    }

    public CountAmount unmatchedInflows(BiQueryScope scope) {
        Query q = em.createNativeQuery("""
                SELECT COUNT(*), COALESCE(SUM(f.amount), 0)
                FROM scf.acct_bank_flow f
                JOIN scf.acct_virtual_account a ON a.id = f.account_id
                WHERE a.operator_id = :operatorId AND a.project_id = :projectId
                  AND f.match_status = 'UNMATCHED' AND f.flow_type = 'IN'
                  AND (:financeFundingPartyId IS NULL OR a.funding_party_id = :financeFundingPartyId)
                """);
        bindScope(q, scope);
        q.setParameter("financeFundingPartyId", scope.financeFundingPartyId());
        return toCountAmount(q.getSingleResult());
    }

    public List<RiskRow> overdueFinances(BiQueryScope scope, int limit) {
        Query q = em.createNativeQuery("""
                SELECT id, finance_no, disbursed_amount
                FROM scf.fn_finance_application
                WHERE operator_id = :operatorId AND project_id = :projectId AND deleted_flag = 0
                  AND finance_status = 'OVERDUE'
                  AND (:financeCustomerId IS NULL OR customer_id = :financeCustomerId)
                  AND (:financeFundingPartyId IS NULL OR funding_party_id = :financeFundingPartyId)
                ORDER BY updated_at DESC NULLS LAST
                LIMIT :limit
                """);
        bindFinanceScope(q, scope);
        q.setParameter("limit", limit);
        return mapRiskFinanceRows(q.getResultList());
    }

    public List<RiskRow> unmatchedFlowSamples(BiQueryScope scope, int limit) {
        Query q = em.createNativeQuery("""
                SELECT f.id, f.external_flow_no, f.amount
                FROM scf.acct_bank_flow f
                JOIN scf.acct_virtual_account a ON a.id = f.account_id
                WHERE a.operator_id = :operatorId AND a.project_id = :projectId
                  AND f.match_status = 'UNMATCHED' AND f.flow_type = 'IN'
                  AND (:financeFundingPartyId IS NULL OR a.funding_party_id = :financeFundingPartyId)
                ORDER BY f.flow_time DESC
                LIMIT :limit
                """);
        bindScope(q, scope);
        q.setParameter("financeFundingPartyId", scope.financeFundingPartyId());
        q.setParameter("limit", limit);
        return mapRiskFlowRows(q.getResultList());
    }

    public List<RiskRow> abnormalPrices(int limit) {
        Query q = em.createNativeQuery("""
                SELECT id, sku_id, price
                FROM scf.pr_price_record
                WHERE abnormal_flag = 1
                ORDER BY price_date DESC, created_at DESC
                LIMIT :limit
                """);
        q.setParameter("limit", limit);
        return mapRiskPriceRows(q.getResultList());
    }

    public List<RiskRow> inventoryExceptions(BiQueryScope scope, int limit) {
        Query q = em.createNativeQuery("""
                SELECT i.id, i.batch_no, i.valuation_amount
                FROM scf.wh_inventory i
                LEFT JOIN scf.wh_warehouse w ON w.id = i.warehouse_id
                WHERE i.deleted_flag = 0 AND i.stocktake_exception = 1
                  AND i.operator_id = :operatorId AND i.project_id = :projectId
                  AND (:inventoryOwnerId IS NULL OR i.owner_id = :inventoryOwnerId)
                  AND (:inventoryWarehouseCompanyId IS NULL OR w.warehouse_company_id = :inventoryWarehouseCompanyId)
                ORDER BY i.updated_at DESC NULLS LAST
                LIMIT :limit
                """);
        bindInventoryScope(q, scope);
        q.setParameter("limit", limit);
        return mapRiskInventoryRows(q.getResultList());
    }

    public long countOverdueFinances(BiQueryScope scope) {
        Query q = em.createNativeQuery("""
                SELECT COUNT(*)
                FROM scf.fn_finance_application
                WHERE operator_id = :operatorId AND project_id = :projectId AND deleted_flag = 0
                  AND finance_status = 'OVERDUE'
                  AND (:financeCustomerId IS NULL OR customer_id = :financeCustomerId)
                  AND (:financeFundingPartyId IS NULL OR funding_party_id = :financeFundingPartyId)
                """);
        bindFinanceScope(q, scope);
        return ((Number) q.getSingleResult()).longValue();
    }

    public long countAbnormalPrices() {
        Query q = em.createNativeQuery("SELECT COUNT(*) FROM scf.pr_price_record WHERE abnormal_flag = 1");
        return ((Number) q.getSingleResult()).longValue();
    }

    private void bindScope(Query q, BiQueryScope scope) {
        q.setParameter("operatorId", scope.operatorId());
        q.setParameter("projectId", scope.projectId());
    }

    private void bindFinanceScope(Query q, BiQueryScope scope) {
        bindScope(q, scope);
        q.setParameter("financeCustomerId", scope.financeCustomerId());
        q.setParameter("financeFundingPartyId", scope.financeFundingPartyId());
    }

    private void bindInventoryScope(Query q, BiQueryScope scope) {
        bindScope(q, scope);
        q.setParameter("inventoryOwnerId", scope.inventoryOwnerId());
        q.setParameter("inventoryWarehouseCompanyId", scope.inventoryWarehouseCompanyId());
    }

    private static CountAmount toCountAmount(Object result) {
        Object[] row = (Object[]) result;
        return new CountAmount(((Number) row[0]).longValue(), toDecimal(row[1]));
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    private static List<TrendRow> mapTrendRows(List<?> rows) {
        List<TrendRow> list = new ArrayList<>();
        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            list.add(new TrendRow(String.valueOf(cols[0]), ((Number) cols[1]).longValue(), toDecimal(cols[2])));
        }
        return list;
    }

    private static List<StatusRow> mapStatusRows(List<?> rows) {
        List<StatusRow> list = new ArrayList<>();
        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            list.add(new StatusRow(String.valueOf(cols[0]), ((Number) cols[1]).longValue(), toDecimal(cols[2])));
        }
        return list;
    }

    private static List<RiskRow> mapRiskFinanceRows(List<?> rows) {
        List<RiskRow> list = new ArrayList<>();
        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            list.add(new RiskRow(String.valueOf(cols[0]), String.valueOf(cols[1]), toDecimal(cols[2]), "FINANCE"));
        }
        return list;
    }

    private static List<RiskRow> mapRiskFlowRows(List<?> rows) {
        List<RiskRow> list = new ArrayList<>();
        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            list.add(new RiskRow(String.valueOf(cols[0]), String.valueOf(cols[1]), toDecimal(cols[2]), "BANK_FLOW"));
        }
        return list;
    }

    private static List<RiskRow> mapRiskPriceRows(List<?> rows) {
        List<RiskRow> list = new ArrayList<>();
        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            list.add(new RiskRow(String.valueOf(cols[0]), String.valueOf(cols[1]), toDecimal(cols[2]), "PRICE"));
        }
        return list;
    }

    private static List<RiskRow> mapRiskInventoryRows(List<?> rows) {
        List<RiskRow> list = new ArrayList<>();
        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            list.add(new RiskRow(String.valueOf(cols[0]), String.valueOf(cols[1]), toDecimal(cols[2]), "INVENTORY"));
        }
        return list;
    }

    public record CountAmount(long count, BigDecimal amount) {}

    public record TrendRow(String period, long count, BigDecimal amount) {}

    public record StatusRow(String status, long count, BigDecimal amount) {}

    public record InventoryTotals(
            long lotCount,
            BigDecimal quantityTotal,
            BigDecimal availableTotal,
            BigDecimal pledgedTotal,
            BigDecimal valuationTotal,
            long stocktakeExceptionCount) {}

    public record ClearingTotals(long executedCount, BigDecimal principal, BigDecimal interest, BigDecimal feeTotal) {}

    public record RiskRow(String id, String label, BigDecimal amount, String relatedType) {}
}
