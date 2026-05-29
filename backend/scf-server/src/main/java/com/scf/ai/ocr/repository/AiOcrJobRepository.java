package com.scf.ai.ocr.repository;

import com.scf.ai.ocr.entity.AiOcrJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiOcrJobRepository extends JpaRepository<AiOcrJob, String> {

    Optional<AiOcrJob> findByIdAndOperatorIdAndProjectId(String id, String operatorId, String projectId);
}
