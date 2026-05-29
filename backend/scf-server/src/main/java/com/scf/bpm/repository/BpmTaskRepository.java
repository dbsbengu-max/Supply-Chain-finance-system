package com.scf.bpm.repository;

import com.scf.bpm.entity.BpmTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BpmTaskRepository extends JpaRepository<BpmTask, String> {
    List<BpmTask> findByAssigneeIdAndApprovalStatus(String assigneeId, String approvalStatus);

    Optional<BpmTask> findByIdAndAssigneeId(String id, String assigneeId);
}
