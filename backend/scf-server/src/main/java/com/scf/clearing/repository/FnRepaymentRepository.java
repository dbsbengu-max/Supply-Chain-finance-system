package com.scf.clearing.repository;

import com.scf.clearing.entity.FnRepayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface FnRepaymentRepository extends JpaRepository<FnRepayment, String> {

    boolean existsByBankFlowIdAndRepaymentStatus(String bankFlowId, String repaymentStatus);

    Optional<FnRepayment> findByBankFlowIdAndRepaymentStatus(String bankFlowId, String repaymentStatus);

    @Query("""
            SELECT COALESCE(SUM(r.amount), 0) FROM FnRepayment r
            WHERE r.financeId = :financeId AND r.repaymentStatus = 'EXECUTED'
            """)
    BigDecimal sumExecutedAmountByFinanceId(@Param("financeId") String financeId);
}
