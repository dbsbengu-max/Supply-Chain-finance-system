package com.scf.document.repository;

import com.scf.document.entity.TrDocumentRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrDocumentRequirementRepository extends JpaRepository<TrDocumentRequirement, String> {

    @Query("""
            SELECT r FROM TrDocumentRequirement r
            WHERE r.operatorId = :operatorId
              AND r.deletedFlag = 0
              AND r.enabled = 1
              AND r.businessType = :businessType
              AND r.businessStage = :businessStage
              AND (:projectId IS NULL OR r.projectId IS NULL OR r.projectId = :projectId)
              AND (:productType IS NULL OR r.productType IS NULL OR r.productType = :productType)
            ORDER BY r.sortNo ASC, r.documentType ASC
            """)
    List<TrDocumentRequirement> findActiveRules(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("businessType") String businessType,
            @Param("businessStage") String businessStage,
            @Param("productType") String productType);

    @Query("""
            SELECT r FROM TrDocumentRequirement r
            WHERE r.operatorId = :operatorId
              AND r.deletedFlag = 0
              AND (:businessType IS NULL OR r.businessType = :businessType)
              AND (:businessStage IS NULL OR r.businessStage = :businessStage)
              AND (:projectId IS NULL OR r.projectId IS NULL OR r.projectId = :projectId)
            ORDER BY r.businessType, r.businessStage, r.sortNo
            """)
    List<TrDocumentRequirement> findManagedList(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("businessType") String businessType,
            @Param("businessStage") String businessStage);

    Optional<TrDocumentRequirement> findByIdAndOperatorIdAndDeletedFlag(
            String id, String operatorId, short deletedFlag);
}
