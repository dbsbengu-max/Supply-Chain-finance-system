package com.scf.trade.repository;

import com.scf.trade.entity.TrOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TrOrderRepository extends JpaRepository<TrOrder, String> {

    Page<TrOrder> findByOperatorIdAndProjectIdAndDeletedFlagOrderByCreatedAtDesc(
            String operatorId, String projectId, short deletedFlag, Pageable pageable);

    @Query("""
            SELECT o FROM TrOrder o
            WHERE o.operatorId = :operatorId AND o.projectId = :projectId AND o.deletedFlag = 0
            AND (o.buyerId = :enterpriseId OR o.sellerId = :enterpriseId OR o.tradeCompanyId = :enterpriseId)
            ORDER BY o.createdAt DESC
            """)
    Page<TrOrder> findByEnterpriseScope(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("enterpriseId") String enterpriseId,
            Pageable pageable);

    Optional<TrOrder> findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
            String id, String operatorId, String projectId, short deletedFlag);
}
