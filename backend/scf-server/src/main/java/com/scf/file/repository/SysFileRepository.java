package com.scf.file.repository;

import com.scf.file.entity.SysFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysFileRepository extends JpaRepository<SysFile, String> {

    Optional<SysFile> findByIdAndOperatorIdAndProjectIdAndStatus(
            String id, String operatorId, String projectId, String status);
}
