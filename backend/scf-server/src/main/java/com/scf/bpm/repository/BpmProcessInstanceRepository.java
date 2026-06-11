package com.scf.bpm.repository;

import com.scf.bpm.entity.BpmProcessInstance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BpmProcessInstanceRepository extends JpaRepository<BpmProcessInstance, String> {

    java.util.Optional<BpmProcessInstance> findTopByBusinessTypeAndBusinessIdOrderByStartedAtDesc(
            String businessType, String businessId);
}
