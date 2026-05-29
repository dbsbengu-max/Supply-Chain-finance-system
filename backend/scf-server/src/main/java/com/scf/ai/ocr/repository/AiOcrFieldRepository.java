package com.scf.ai.ocr.repository;

import com.scf.ai.ocr.entity.AiOcrField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiOcrFieldRepository extends JpaRepository<AiOcrField, String> {

    List<AiOcrField> findByJobIdOrderByFieldNameAsc(String jobId);
}
