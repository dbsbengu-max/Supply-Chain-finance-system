package com.scf.customer.repository;

import com.scf.customer.entity.MdEnterpriseCert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MdEnterpriseCertRepository extends JpaRepository<MdEnterpriseCert, String> {

    List<MdEnterpriseCert> findByEnterpriseIdAndDeletedFlag(String enterpriseId, short deletedFlag);
}
