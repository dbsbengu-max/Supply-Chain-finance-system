package com.scf.saga.service;

import com.scf.saga.entity.BizCompensationTask;
import com.scf.saga.repository.BizCompensationTaskRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class CompensationTaskExecutor {

    private static final int BATCH_SIZE = 20;

    private final BizCompensationTaskRepository repository;
    private final CompensationTaskProcessor processor;

    public CompensationTaskExecutor(
            BizCompensationTaskRepository repository,
            CompensationTaskProcessor processor) {
        this.repository = repository;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${scf.compensation.poll-interval-ms:30000}")
    public void executeReadyTasks() {
        List<BizCompensationTask> tasks = repository.findReadyTasks(
                Instant.now(), PageRequest.of(0, BATCH_SIZE));
        for (BizCompensationTask task : tasks) {
            processor.process(task.getId());
        }
    }
}
