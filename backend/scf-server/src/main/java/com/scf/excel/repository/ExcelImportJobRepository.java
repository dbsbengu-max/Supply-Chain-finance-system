package com.scf.excel.repository;

import com.scf.excel.entity.ExcelImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExcelImportJobRepository extends JpaRepository<ExcelImportJob, String> {

    Optional<ExcelImportJob> findByIdAndOperatorIdAndProjectId(String id, String operatorId, String projectId);
}
