package com.scf.iam.repository;

import com.scf.iam.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, String> {
    Optional<SysUser> findByLoginNameAndStatus(String loginName, String status);
}
