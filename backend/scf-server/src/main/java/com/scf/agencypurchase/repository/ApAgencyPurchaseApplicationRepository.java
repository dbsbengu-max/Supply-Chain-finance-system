package com.scf.agencypurchase.repository;

import com.scf.agencypurchase.entity.ApAgencyPurchaseApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ApAgencyPurchaseApplicationRepository extends JpaRepository<ApAgencyPurchaseApplication, String> {

    Optional<ApAgencyPurchaseApplication> findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
            String id, String operatorId, String projectId, short deletedFlag);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM ApAgencyPurchaseApplication a
            WHERE a.id = :id AND a.deletedFlag = 0
            """)
    Optional<ApAgencyPurchaseApplication> findByIdForUpdate(@Param("id") String id);

    @Query("""
            SELECT a FROM ApAgencyPurchaseApplication a
            WHERE a.operatorId = :operatorId AND a.projectId = :projectId AND a.deletedFlag = 0
            AND (:status = '' OR a.applicationStatus = :status)
            AND (:sagaStatus = '' OR COALESCE(a.sagaStatus, '') = :sagaStatus)
            AND (:orderMode = '' OR a.orderMode = :orderMode)
            AND (:fundSource = '' OR a.fundSource = :fundSource)
            AND (:pickupType = '' OR a.pickupType = :pickupType)
            AND (:customerId = '' OR a.customerId = :customerId)
            AND a.createdAt >= :createdFrom
            AND a.createdAt <= :createdTo
            ORDER BY a.createdAt DESC
            """)
    Page<ApAgencyPurchaseApplication> search(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("status") String status,
            @Param("sagaStatus") String sagaStatus,
            @Param("orderMode") String orderMode,
            @Param("fundSource") String fundSource,
            @Param("pickupType") String pickupType,
            @Param("customerId") String customerId,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            Pageable pageable);

    @Query("""
            SELECT a FROM ApAgencyPurchaseApplication a
            WHERE a.operatorId = :operatorId AND a.projectId = :projectId AND a.deletedFlag = 0
            AND a.customerId = :customerId
            AND (:status = '' OR a.applicationStatus = :status)
            AND (:sagaStatus = '' OR COALESCE(a.sagaStatus, '') = :sagaStatus)
            AND (:orderMode = '' OR a.orderMode = :orderMode)
            AND (:fundSource = '' OR a.fundSource = :fundSource)
            AND (:pickupType = '' OR a.pickupType = :pickupType)
            AND a.createdAt >= :createdFrom
            AND a.createdAt <= :createdTo
            ORDER BY a.createdAt DESC
            """)
    Page<ApAgencyPurchaseApplication> searchByCustomer(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("customerId") String customerId,
            @Param("status") String status,
            @Param("sagaStatus") String sagaStatus,
            @Param("orderMode") String orderMode,
            @Param("fundSource") String fundSource,
            @Param("pickupType") String pickupType,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            Pageable pageable);
}
