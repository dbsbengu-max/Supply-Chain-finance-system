package com.scf.finance.repository;

import com.scf.finance.entity.AcctBankFlow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AcctBankFlowRepository extends JpaRepository<AcctBankFlow, String> {

    List<AcctBankFlow> findBySourceTypeAndSourceId(String sourceType, String sourceId);

    boolean existsByAccountIdAndExternalFlowNo(String accountId, String externalFlowNo);

    @Query("""
            SELECT f FROM AcctBankFlow f, AcctVirtualAccount a
            WHERE f.accountId = a.id
            AND a.operatorId = :operatorId AND a.projectId = :projectId
            AND (:matchStatus IS NULL OR f.matchStatus = :matchStatus)
            AND (:flowType IS NULL OR f.flowType = :flowType)
            AND (:accountId IS NULL OR f.accountId = :accountId)
            ORDER BY f.flowTime DESC
            """)
    Page<AcctBankFlow> findScoped(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("matchStatus") String matchStatus,
            @Param("flowType") String flowType,
            @Param("accountId") String accountId,
            Pageable pageable);

    @Query("""
            SELECT f FROM AcctBankFlow f, AcctVirtualAccount a
            WHERE f.accountId = a.id
            AND a.operatorId = :operatorId AND a.projectId = :projectId
            AND f.matchStatus = 'UNMATCHED' AND f.flowType = 'IN'
            ORDER BY f.flowTime DESC
            """)
    List<AcctBankFlow> findUnmatchedInFlows(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId);
}
