package com.scf.agencypurchase.repository;

import com.scf.agencypurchase.entity.ApAgencyPurchaseSagaStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApAgencyPurchaseSagaStepRepository extends JpaRepository<ApAgencyPurchaseSagaStep, String> {

    List<ApAgencyPurchaseSagaStep> findByApplicationIdOrderByCreatedAtAsc(String applicationId);

    Optional<ApAgencyPurchaseSagaStep> findByApplicationIdAndStepCode(String applicationId, String stepCode);
}
