package com.scf.warehouse.repository;

import com.scf.warehouse.entity.WhWarehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WhWarehouseRepository extends JpaRepository<WhWarehouse, String> {

    @Query("""
            SELECT w FROM WhWarehouse w
            WHERE w.operatorId = :operatorId
              AND (:projectId = '' OR w.projectId = :projectId)
              AND (:warehouseCompanyId = '' OR w.warehouseCompanyId = :warehouseCompanyId)
              AND (:status = '' OR w.status = :status)
            ORDER BY w.warehouseCode
            """)
    Page<WhWarehouse> search(
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("warehouseCompanyId") String warehouseCompanyId,
            @Param("status") String status,
            Pageable pageable);

    Optional<WhWarehouse> findByIdAndOperatorIdAndProjectId(String id, String operatorId, String projectId);
}
