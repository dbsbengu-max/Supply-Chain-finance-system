package com.scf.saga.service;

import com.scf.common.util.IdGenerator;
import com.scf.saga.entity.BizCompensationTask;
import com.scf.saga.entity.BizEventOutbox;
import com.scf.saga.repository.BizCompensationTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CompensationTaskService {

    private final BizCompensationTaskRepository repository;

    public CompensationTaskService(BizCompensationTaskRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public BizCompensationTask enqueue(
            BizEventOutbox sourceEvent,
            String compensationType,
            String businessType,
            String businessId,
            String actionJson) {
        BizCompensationTask task = new BizCompensationTask();
        task.setId(IdGenerator.nextId());
        task.setSourceEventId(sourceEvent == null ? null : sourceEvent.getId());
        task.setCompensationType(compensationType);
        task.setBusinessType(businessType);
        task.setBusinessId(businessId);
        task.setCompensationStatus("PENDING");
        task.setActionJson(actionJson);
        task.setCreatedAt(Instant.now());
        return repository.save(task);
    }
}
