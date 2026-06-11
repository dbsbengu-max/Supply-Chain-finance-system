package com.scf.contract.repository;

import com.scf.contract.entity.TrContractSignTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrContractSignTaskRepository extends JpaRepository<TrContractSignTask, String> {

    List<TrContractSignTask> findByDocumentIdOrderByCreatedAtDesc(String documentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TrContractSignTask t WHERE t.externalSignRef = :externalSignRef")
    Optional<TrContractSignTask> findByExternalSignRefForUpdate(@Param("externalSignRef") String externalSignRef);

    Optional<TrContractSignTask> findTopByDocumentIdAndTaskStatusInOrderByCreatedAtDesc(
            String documentId, List<String> taskStatuses);

    Optional<TrContractSignTask> findFirstByExternalSignRefOrderByCreatedAtDesc(String externalSignRef);
}
