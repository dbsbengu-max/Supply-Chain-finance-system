package com.scf.clearing.repository;

import com.scf.clearing.entity.ClearingResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ClearingResultRepository extends JpaRepository<ClearingResult, String> {

    @Query("""
            SELECT COALESCE(SUM(cr.principalAmount), 0) FROM ClearingResult cr, FnRepayment r
            WHERE r.id = cr.repaymentId
            AND r.financeId = :financeId
            AND cr.clearingStatus = 'EXECUTED'
            """)
    BigDecimal sumExecutedPrincipalByFinanceId(@Param("financeId") String financeId);
}
