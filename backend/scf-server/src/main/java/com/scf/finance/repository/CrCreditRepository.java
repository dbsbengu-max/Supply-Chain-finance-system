package com.scf.finance.repository;

import com.scf.finance.entity.CrCredit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CrCreditRepository extends JpaRepository<CrCredit, String> {

    Optional<CrCredit> findByIdAndOperatorIdAndProjectId(String id, String operatorId, String projectId);
}
