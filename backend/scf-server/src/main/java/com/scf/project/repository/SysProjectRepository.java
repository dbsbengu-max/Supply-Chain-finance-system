package com.scf.project.repository;

import com.scf.project.entity.SysProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysProjectRepository extends JpaRepository<SysProject, String> {

    List<SysProject> findByOperatorIdAndDeletedFlagOrderByCreatedAtDesc(String operatorId, short deletedFlag);

    Optional<SysProject> findByIdAndOperatorIdAndDeletedFlag(String id, String operatorId, short deletedFlag);

    boolean existsByOperatorIdAndProjectCodeAndDeletedFlag(String operatorId, String projectCode, short deletedFlag);
}
