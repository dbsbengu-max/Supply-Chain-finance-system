package com.scf.voucher.repository;

import com.scf.voucher.entity.DvVoucherFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface DvVoucherFlowRepository extends JpaRepository<DvVoucherFlow, String> {

    List<DvVoucherFlow> findByVoucherIdOrderByOperatedAtDesc(String voucherId);

    boolean existsByVoucherIdAndFlowTypeAndRelatedVoucherId(
            String voucherId, String flowType, String relatedVoucherId);

    @Query("""
            SELECT COALESCE(SUM(f.amount), 0) FROM DvVoucherFlow f
            WHERE f.voucherId = :voucherId AND f.flowType = :flowType
            """)
    BigDecimal sumAmountByVoucherIdAndFlowType(
            @Param("voucherId") String voucherId,
            @Param("flowType") String flowType);
}
