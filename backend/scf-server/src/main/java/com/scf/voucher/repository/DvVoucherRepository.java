package com.scf.voucher.repository;

import com.scf.voucher.entity.DvVoucher;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DvVoucherRepository extends JpaRepository<DvVoucher, String> {

    @Query("""
            SELECT v FROM DvVoucher v
            WHERE v.operatorId = :operatorId AND v.projectId = :projectId
              AND (:status IS NULL OR v.voucherStatus = :status)
              AND (:holderId IS NULL OR v.holderId = :holderId)
              AND (:voucherNo IS NULL OR LOWER(v.voucherNo) LIKE LOWER(CONCAT('%', :voucherNo, '%')))
            ORDER BY v.issueDate DESC, v.voucherNo DESC
            """)
    Page<DvVoucher> findProjectScope(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("status") String status,
            @Param("holderId") String holderId,
            @Param("voucherNo") String voucherNo,
            Pageable pageable);

    @Query("""
            SELECT v FROM DvVoucher v
            WHERE v.operatorId = :operatorId AND v.projectId = :projectId
              AND (v.issuerId = :enterpriseId OR v.acceptorId = :enterpriseId OR v.holderId = :enterpriseId)
              AND (:status IS NULL OR v.voucherStatus = :status)
              AND (:voucherNo IS NULL OR LOWER(v.voucherNo) LIKE LOWER(CONCAT('%', :voucherNo, '%')))
            ORDER BY v.issueDate DESC, v.voucherNo DESC
            """)
    Page<DvVoucher> findEnterpriseScope(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("enterpriseId") String enterpriseId,
            @Param("status") String status,
            @Param("voucherNo") String voucherNo,
            Pageable pageable);

    Optional<DvVoucher> findByIdAndOperatorIdAndProjectId(String id, String operatorId, String projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT v FROM DvVoucher v
            WHERE v.id = :id AND v.operatorId = :operatorId AND v.projectId = :projectId
            """)
    Optional<DvVoucher> findByIdForUpdate(
            @Param("id") String id,
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM DvVoucher v WHERE v.id = :id")
    Optional<DvVoucher> findByIdForUpdate(@Param("id") String id);
}
