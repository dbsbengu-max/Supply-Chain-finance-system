package com.scf.clearing.repository;

import com.scf.clearing.entity.ClearingRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClearingRuleRepository extends JpaRepository<ClearingRule, String> {

    @Query("""
            SELECT r FROM ClearingRule r
            WHERE r.operatorId = :operatorId AND r.projectId = :projectId
            AND (:productType IS NULL OR r.productType = :productType)
            AND (:reviewStatus IS NULL OR r.reviewStatus = :reviewStatus)
            AND (
                :fundingScopeId IS NULL
                OR r.fundingPartyId IS NULL
                OR r.fundingPartyId = :fundingScopeId
            )
            ORDER BY r.effectiveFrom DESC, r.ruleName ASC
            """)
    Page<ClearingRule> findScoped(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("productType") String productType,
            @Param("reviewStatus") String reviewStatus,
            @Param("fundingScopeId") String fundingScopeId,
            Pageable pageable);

    @Query("""
            SELECT r FROM ClearingRule r
            WHERE r.operatorId = :operatorId AND r.projectId = :projectId
            AND r.reviewStatus = 'APPROVED'
            AND (:productType IS NULL OR r.productType = :productType)
            AND (:fundingPartyId IS NULL OR r.fundingPartyId IS NULL OR r.fundingPartyId = :fundingPartyId)
            ORDER BY r.effectiveFrom DESC
            """)
    List<ClearingRule> findApprovedRules(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("productType") String productType,
            @Param("fundingPartyId") String fundingPartyId);

    Optional<ClearingRule> findByIdAndOperatorIdAndProjectId(String id, String operatorId, String projectId);
}
