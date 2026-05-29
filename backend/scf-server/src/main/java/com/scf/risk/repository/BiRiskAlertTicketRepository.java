package com.scf.risk.repository;

import com.scf.risk.entity.BiRiskAlertTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BiRiskAlertTicketRepository extends JpaRepository<BiRiskAlertTicket, String> {

    Optional<BiRiskAlertTicket> findByOperatorIdAndProjectIdAndAlertKey(
            String operatorId, String projectId, String alertKey);

    Optional<BiRiskAlertTicket> findByIdAndOperatorIdAndProjectId(String id, String operatorId, String projectId);

    @Query("""
            SELECT t FROM BiRiskAlertTicket t
            WHERE t.operatorId = :operatorId AND t.projectId = :projectId
              AND (:alertCode IS NULL OR t.alertCode = :alertCode)
              AND (:severity IS NULL OR t.severity = :severity)
              AND (:handleStatus IS NULL OR t.handleStatus = :handleStatus)
              AND (:assigneeUserId IS NULL OR t.assigneeUserId = :assigneeUserId)
            ORDER BY
              CASE t.severity WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,
              t.detectedAt DESC
            """)
    Page<BiRiskAlertTicket> findFiltered(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("alertCode") String alertCode,
            @Param("severity") String severity,
            @Param("handleStatus") String handleStatus,
            @Param("assigneeUserId") String assigneeUserId,
            Pageable pageable);
}
