package com.scf.saga.repository;

import com.scf.saga.entity.BizCompensationTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BizCompensationTaskRepository extends JpaRepository<BizCompensationTask, String> {

    List<BizCompensationTask> findByBusinessTypeAndBusinessIdOrderByCreatedAtDesc(
            String businessType, String businessId);

    List<BizCompensationTask> findByCompensationStatusOrderByCreatedAtAsc(
            String compensationStatus, Pageable pageable);
}
