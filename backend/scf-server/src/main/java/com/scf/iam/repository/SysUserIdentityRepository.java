package com.scf.iam.repository;

import com.scf.iam.entity.SysUserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysUserIdentityRepository extends JpaRepository<SysUserIdentity, String> {
    List<SysUserIdentity> findByUserIdAndStatus(String userId, String status);

    Optional<SysUserIdentity> findByIdAndUserIdAndStatus(String id, String userId, String status);
}
