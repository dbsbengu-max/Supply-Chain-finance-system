package com.scf.warehouse.repository;

import com.scf.warehouse.entity.WhInventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface WhInventoryRepository extends JpaRepository<WhInventory, String> {

    @Query("""
            SELECT i FROM WhInventory i, WhWarehouse w
            WHERE i.warehouseId = w.id
              AND i.deletedFlag = 0
              AND i.operatorId = :operatorId
              AND i.projectId = :projectId
              AND (:warehouseId = '' OR i.warehouseId = :warehouseId)
              AND (:rightStatus = '' OR i.rightStatus = :rightStatus)
              AND (:ownerId = '' OR i.ownerId = :ownerId)
              AND (:warehouseCompanyId = '' OR w.warehouseCompanyId = :warehouseCompanyId)
            ORDER BY i.id DESC
            """)
    Page<WhInventory> search(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("warehouseId") String warehouseId,
            @Param("rightStatus") String rightStatus,
            @Param("ownerId") String ownerId,
            @Param("warehouseCompanyId") String warehouseCompanyId,
            Pageable pageable);

    Optional<WhInventory> findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
            String id, String operatorId, String projectId, short deletedFlag);

    boolean existsByWarehouseIdAndSkuIdAndBatchNoAndDeletedFlag(
            String warehouseId, String skuId, String batchNo, short deletedFlag);

    @Query("""
            SELECT i FROM WhInventory i, WhWarehouse w
            WHERE i.warehouseId = w.id
              AND i.deletedFlag = 0
              AND i.stocktakeException = 1
              AND i.operatorId = :operatorId
              AND i.projectId = :projectId
              AND (:ownerId = '' OR i.ownerId = :ownerId)
              AND (:warehouseCompanyId = '' OR w.warehouseCompanyId = :warehouseCompanyId)
            ORDER BY i.updatedAt DESC, i.id DESC
            """)
    List<WhInventory> findStocktakeExceptions(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("ownerId") String ownerId,
            @Param("warehouseCompanyId") String warehouseCompanyId,
            Pageable pageable);
}
