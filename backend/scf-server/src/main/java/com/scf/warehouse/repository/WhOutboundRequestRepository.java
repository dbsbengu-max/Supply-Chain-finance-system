package com.scf.warehouse.repository;

import com.scf.warehouse.entity.WhOutboundRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhOutboundRequestRepository extends JpaRepository<WhOutboundRequest, String> {

    Optional<WhOutboundRequest> findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
            String id, String operatorId, String projectId, short deletedFlag);
}
