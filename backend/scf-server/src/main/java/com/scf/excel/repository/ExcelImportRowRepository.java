package com.scf.excel.repository;

import com.scf.excel.entity.ExcelImportRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExcelImportRowRepository extends JpaRepository<ExcelImportRow, String> {

    List<ExcelImportRow> findByJobIdOrderByRowNoAsc(String jobId);
}
