package com.scf.finance.repository;

import com.scf.finance.entity.FnFinanceApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface FnFinanceApplicationRepository extends JpaRepository<FnFinanceApplication, String> {

    Page<FnFinanceApplication> findByOperatorIdAndProjectIdAndDeletedFlagOrderByCreatedAtDesc(
            String operatorId, String projectId, short deletedFlag, Pageable pageable);

    @Query("""
            SELECT f FROM FnFinanceApplication f
            WHERE f.operatorId = :operatorId AND f.projectId = :projectId AND f.deletedFlag = 0
            AND f.customerId = :enterpriseId
            ORDER BY f.createdAt DESC
            """)
    Page<FnFinanceApplication> findByCustomerScope(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("enterpriseId") String enterpriseId,
            Pageable pageable);

    @Query("""
            SELECT f FROM FnFinanceApplication f
            WHERE f.operatorId = :operatorId AND f.projectId = :projectId AND f.deletedFlag = 0
            AND f.fundingPartyId = :fundingPartyId
            ORDER BY f.createdAt DESC
            """)
    Page<FnFinanceApplication> findByFundingScope(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("fundingPartyId") String fundingPartyId,
            Pageable pageable);

    Optional<FnFinanceApplication> findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
            String id, String operatorId, String projectId, short deletedFlag);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT f FROM FnFinanceApplication f
            WHERE f.id = :id AND f.operatorId = :operatorId AND f.projectId = :projectId AND f.deletedFlag = 0
            """)
    Optional<FnFinanceApplication> findByIdAndOperatorIdAndProjectIdForUpdate(
            @Param("id") String id,
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FnFinanceApplication f WHERE f.id = :id AND f.deletedFlag = 0")
    Optional<FnFinanceApplication> findByIdForUpdate(@Param("id") String id);

    @Query("SELECT f.financeStatus FROM FnFinanceApplication f WHERE f.id = :financeId")
    Optional<String> findFinanceStatusById(@Param("financeId") String financeId);

    @Query("""
            SELECT f FROM FnFinanceApplication f
            WHERE f.operatorId = :operatorId AND f.projectId = :projectId AND f.deletedFlag = 0
              AND f.financeStatus = 'TO_DISBURSE'
              AND (:fundingPartyId IS NULL OR f.fundingPartyId = :fundingPartyId)
              AND (:customerId IS NULL OR f.customerId = :customerId)
            ORDER BY f.createdAt DESC
            """)
    List<FnFinanceApplication> findToDisburseInScope(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("fundingPartyId") String fundingPartyId,
            @Param("customerId") String customerId,
            Pageable pageable);

    List<FnFinanceApplication> findByOperatorIdAndProjectIdAndSourceTypeAndSourceIdAndDeletedFlagOrderByCreatedAtDesc(
            String operatorId, String projectId, String sourceType, String sourceId, short deletedFlag);
}
