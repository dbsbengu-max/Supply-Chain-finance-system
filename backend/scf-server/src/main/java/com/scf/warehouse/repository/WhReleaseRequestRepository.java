package com.scf.warehouse.repository;

import com.scf.warehouse.entity.WhReleaseRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhReleaseRequestRepository extends JpaRepository<WhReleaseRequest, String> {

    Optional<WhReleaseRequest> findByInventoryIdAndRequestStatusAndDeletedFlag(
            String inventoryId, String requestStatus, short deletedFlag);
}
