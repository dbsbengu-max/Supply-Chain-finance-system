package com.scf.saga.repository;

import com.scf.saga.entity.BizCompensationTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface BizCompensationTaskRepository extends JpaRepository<BizCompensationTask, String> {

    List<BizCompensationTask> findByBusinessTypeAndBusinessIdOrderByCreatedAtDesc(
            String businessType, String businessId);

    @Query("""
            select t from BizCompensationTask t
            where t.compensationStatus = 'PENDING'
               or (t.compensationStatus = 'FAILED'
                   and (t.nextRetryAt is null or t.nextRetryAt <= :now))
            order by t.createdAt asc
            """)
    List<BizCompensationTask> findReadyTasks(Instant now, Pageable pageable);

    @Query("""
            select t from BizCompensationTask t
            where (:status is null or t.compensationStatus = :status)
              and (:businessType is null or t.businessType = :businessType)
              and (:compensationType is null or t.compensationType = :compensationType)
              and (:businessId is null or t.businessId = :businessId)
            order by t.createdAt desc
            """)
    Page<BizCompensationTask> search(
            String status,
            String businessType,
            String compensationType,
            String businessId,
            Pageable pageable);

    long countByCompensationStatus(String compensationStatus);
}
