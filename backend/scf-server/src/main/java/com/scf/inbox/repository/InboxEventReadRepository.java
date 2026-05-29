package com.scf.inbox.repository;

import com.scf.inbox.entity.InboxEventRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InboxEventReadRepository extends JpaRepository<InboxEventRead, String> {

    Optional<InboxEventRead> findByUserIdAndOperatorIdAndProjectIdAndEventKey(
            String userId, String operatorId, String projectId, String eventKey);

    @Query("""
            SELECT r.eventKey FROM InboxEventRead r
            WHERE r.userId = :userId
              AND r.operatorId = :operatorId
              AND r.projectId = :projectId
              AND r.eventKey IN :eventKeys
            """)
    List<String> findReadEventKeys(
            @Param("userId") String userId,
            @Param("operatorId") String operatorId,
            @Param("projectId") String projectId,
            @Param("eventKeys") Collection<String> eventKeys);
}
