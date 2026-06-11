package com.scf.trade.repository;

import com.scf.trade.entity.TrDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrDocumentRepository extends JpaRepository<TrDocument, String> {

    List<TrDocument> findByBusinessTypeAndBusinessIdAndDeletedFlag(
            String businessType, String businessId, short deletedFlag);

    List<TrDocument> findByOperatorIdAndProjectIdAndBusinessTypeAndBusinessIdAndDeletedFlag(
            String operatorId, String projectId, String businessType, String businessId, short deletedFlag);

    Optional<TrDocument> findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
            String id, String operatorId, String projectId, short deletedFlag);

    @Query("""
            SELECT d FROM TrDocument d
            WHERE d.operatorId = :operatorId
              AND d.projectId = :projectId
              AND d.deletedFlag = 0
              AND (:businessType IS NULL OR d.businessType = :businessType)
              AND (:businessId IS NULL OR d.businessId = :businessId)
              AND (:documentType IS NULL OR d.documentType = :documentType)
              AND (:documentStatus IS NULL OR d.documentStatus = :documentStatus)
              AND (:reviewStatus IS NULL OR d.reviewStatus = :reviewStatus)
              AND (:contractStatus IS NULL OR d.contractStatus = :contractStatus)
              AND (
                :enterpriseScope IS NULL OR
                d.createdBy = :userScope OR
                (d.businessType = 'TRADE_ORDER' AND d.businessId IN :accessibleOrderIds)
              )
            ORDER BY d.createdAt DESC
            """)
    Page<TrDocument> findCenterFiltered(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("businessType") String businessType,
            @Param("businessId") String businessId,
            @Param("documentType") String documentType,
            @Param("documentStatus") String documentStatus,
            @Param("reviewStatus") String reviewStatus,
            @Param("contractStatus") String contractStatus,
            @Param("enterpriseScope") String enterpriseScope,
            @Param("userScope") String userScope,
            @Param("accessibleOrderIds") List<String> accessibleOrderIds,
            Pageable pageable);
}
