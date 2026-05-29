package com.scf.saga.repository;

import com.scf.saga.entity.BizEventOutbox;
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
}
