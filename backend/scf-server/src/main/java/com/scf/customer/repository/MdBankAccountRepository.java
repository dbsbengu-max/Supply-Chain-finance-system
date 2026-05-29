package com.scf.customer.repository;

import com.scf.customer.entity.MdBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MdBankAccountRepository extends JpaRepository<MdBankAccount, String> {

    List<MdBankAccount> findByEnterpriseIdAndDeletedFlag(String enterpriseId, short deletedFlag);
}
