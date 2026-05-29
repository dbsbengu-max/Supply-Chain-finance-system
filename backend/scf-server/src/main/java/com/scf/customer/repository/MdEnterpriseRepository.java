package com.scf.customer.repository;

import com.scf.customer.entity.MdEnterprise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MdEnterpriseRepository extends JpaRepository<MdEnterprise, String> {

    Page<MdEnterprise> findByOperatorIdAndDeletedFlag(String operatorId, short deletedFlag, Pageable pageable);

    Page<MdEnterprise> findByOperatorIdAndEnterpriseTypeAndDeletedFlag(
            String operatorId, String enterpriseType, short deletedFlag, Pageable pageable);

    Page<MdEnterprise> findByOperatorIdAndKycStatusAndDeletedFlag(
            String operatorId, String kycStatus, short deletedFlag, Pageable pageable);

    Optional<MdEnterprise> findByIdAndOperatorIdAndDeletedFlag(String id, String operatorId, short deletedFlag);
}
