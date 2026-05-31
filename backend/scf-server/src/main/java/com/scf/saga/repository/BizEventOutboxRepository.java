package com.scf.saga.repository;

import com.scf.saga.entity.BizEventOutbox;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface BizEventOutboxRepository extends JpaRepository<BizEventOutbox, String> {

    @Query("""
            select e from BizEventOutbox e
            where e.eventStatus in ('PENDING', 'FAILED')
              and (e.nextRetryAt is null or e.nextRetryAt <= :now)
            order by e.createdAt asc
            """)
    List<BizEventOutbox> findPendingEvents(Instant now);

    @Query("""
            select e from BizEventOutbox e
            where (:status is null or e.eventStatus = :status)
              and (:eventType is null or e.eventType = :eventType)
              and (:businessType is null or e.businessType = :businessType)
              and (:businessId is null or e.businessId = :businessId)
            order by e.createdAt desc
            """)
    Page<BizEventOutbox> search(
            String status,
            String eventType,
            String businessType,
            String businessId,
            Pageable pageable);

    long countByEventStatus(String eventStatus);
}
