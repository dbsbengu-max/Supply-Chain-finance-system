package com.scf.document.repository;

import com.scf.document.entity.TrDocumentReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrDocumentReviewLogRepository extends JpaRepository<TrDocumentReviewLog, String> {

    List<TrDocumentReviewLog> findByDocumentIdOrderByCreatedAtDesc(String documentId);
}
