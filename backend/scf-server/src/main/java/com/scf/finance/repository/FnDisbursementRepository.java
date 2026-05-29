package com.scf.finance.repository;

import com.scf.finance.entity.FnDisbursement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface FnDisbursementRepository extends JpaRepository<FnDisbursement, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM FnDisbursement d WHERE d.channelRequestId = :channelRequestId")
    Optional<FnDisbursement> findByChannelRequestIdForUpdate(@Param("channelRequestId") String channelRequestId);

    @Query("""
            SELECT d FROM FnDisbursement d, FnFinanceApplication f
            WHERE d.financeId = f.id
              AND f.operatorId = :operatorId
              AND f.projectId = :projectId
              AND f.deletedFlag = 0
              AND d.disbursementStatus = 'PENDING'
              AND (:fundingPartyId IS NULL OR f.fundingPartyId = :fundingPartyId)
            ORDER BY d.createdAt DESC
            """)
    List<FnDisbursement> findPendingInScope(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("fundingPartyId") String fundingPartyId,
            Pageable pageable);
}
