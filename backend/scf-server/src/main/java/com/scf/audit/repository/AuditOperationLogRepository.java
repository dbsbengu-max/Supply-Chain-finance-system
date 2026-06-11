package com.scf.audit.repository;

import com.scf.audit.entity.AuditOperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuditOperationLogRepository extends JpaRepository<AuditOperationLog, String> {

    Optional<AuditOperationLog> findByIdAndOperatorId(String id, String operatorId);

    @Query("""
            SELECT l FROM AuditOperationLog l
            WHERE l.operatorId = :operatorId
              AND (:projectId IS NULL OR l.projectId IS NULL OR l.projectId = :projectId)
              AND (:enterpriseScope IS NULL OR l.enterpriseId = :enterpriseScope OR l.userId = :userScope)
              AND (:action IS NULL OR l.action = :action)
              AND (:objectType IS NULL OR l.objectType = :objectType)
              AND (:objectId IS NULL OR l.objectId = :objectId)
              AND (:userId IS NULL OR l.userId = :userId)
              AND (:fromAt IS NULL OR l.operationAt >= :fromAt)
              AND (:toAt IS NULL OR l.operationAt <= :toAt)
              AND (
                :keyword IS NULL OR :keyword = '' OR
                LOWER(l.action) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                LOWER(l.objectType) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                LOWER(l.objectId) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY l.operationAt DESC
            """)
    Page<AuditOperationLog> findFiltered(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("enterpriseScope") String enterpriseScope,
            @Param("userScope") String userScope,
            @Param("action") String action,
            @Param("objectType") String objectType,
            @Param("objectId") String objectId,
            @Param("userId") String userId,
            @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            SELECT l.objectType, COUNT(l) FROM AuditOperationLog l
            WHERE l.operatorId = :operatorId
              AND (:projectId IS NULL OR l.projectId IS NULL OR l.projectId = :projectId)
              AND (:enterpriseScope IS NULL OR l.enterpriseId = :enterpriseScope OR l.userId = :userScope)
              AND l.operationAt >= :fromAt
            GROUP BY l.objectType
            ORDER BY COUNT(l) DESC
            """)
    List<Object[]> countByObjectTypeSince(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("enterpriseScope") String enterpriseScope,
            @Param("userScope") String userScope,
            @Param("fromAt") Instant fromAt);

    @Query("""
            SELECT DISTINCT l.action FROM AuditOperationLog l
            WHERE l.operatorId = :operatorId
            ORDER BY l.action
            """)
    List<String> distinctActions(@Param("operatorId") String operatorId);

    @Query("""
            SELECT DISTINCT l.objectType FROM AuditOperationLog l
            WHERE l.operatorId = :operatorId
            ORDER BY l.objectType
            """)
    List<String> distinctObjectTypes(@Param("operatorId") String operatorId);

    @Query("""
            SELECT l FROM AuditOperationLog l
            WHERE l.objectType = :objectType AND l.objectId = :objectId
            ORDER BY l.operationAt DESC
            """)
    List<AuditOperationLog> findByObjectTypeAndObjectIdOrderByOperationAtDesc(
            @Param("objectType") String objectType,
            @Param("objectId") String objectId,
            Pageable pageable);
}
