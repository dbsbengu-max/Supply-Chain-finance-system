package com.scf.finance.repository;

import com.scf.finance.entity.AcctVirtualAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AcctVirtualAccountRepository extends JpaRepository<AcctVirtualAccount, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM AcctVirtualAccount a
            WHERE a.id = :id AND a.operatorId = :operatorId AND a.projectId = :projectId
            """)
    Optional<AcctVirtualAccount> findByIdAndOperatorIdAndProjectId(
            @Param("id") String id,
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId);

    @Query("""
            SELECT a FROM AcctVirtualAccount a
            WHERE a.id = :id AND a.operatorId = :operatorId AND a.projectId = :projectId
            """)
    Optional<AcctVirtualAccount> findScopedById(
            @Param("id") String id,
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AcctVirtualAccount a WHERE a.id = :id")
    Optional<AcctVirtualAccount> findByIdForUpdate(@Param("id") String id);

    List<AcctVirtualAccount> findByOperatorIdAndProjectIdAndFundingPartyId(
            String operatorId, String projectId, String fundingPartyId);

    @Query("""
            SELECT a FROM AcctVirtualAccount a
            WHERE a.operatorId = :operatorId AND a.projectId = :projectId
            AND a.accountType = :accountType AND a.status = 'ACTIVE'
            """)
    List<AcctVirtualAccount> findByProjectAndAccountType(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("accountType") String accountType);

    @Query("""
            SELECT a FROM AcctVirtualAccount a
            WHERE a.operatorId = :operatorId AND a.projectId = :projectId
            AND a.status = 'ACTIVE'
            AND (
                :fundingScopeId IS NULL
                OR a.fundingPartyId = :fundingScopeId
            )
            ORDER BY a.accountType ASC, a.accountName ASC
            """)
    List<AcctVirtualAccount> findSummaryAccounts(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("fundingScopeId") String fundingScopeId);
}
