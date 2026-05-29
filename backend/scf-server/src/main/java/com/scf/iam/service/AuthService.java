package com.scf.iam.service;

import com.scf.common.exception.BusinessException;
import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import com.scf.iam.dto.IdentityView;
import com.scf.iam.dto.LoginRequest;
import com.scf.iam.dto.LoginResponse;
import com.scf.iam.entity.SysRole;
import com.scf.iam.entity.SysUser;
import com.scf.iam.entity.SysUserIdentity;
import com.scf.iam.repository.SysRoleRepository;
import com.scf.iam.repository.SysUserIdentityRepository;
import com.scf.iam.repository.SysUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private final SysUserRepository userRepository;
    private final SysUserIdentityRepository identityRepository;
    private final SysRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            SysUserRepository userRepository,
            SysUserIdentityRepository identityRepository,
            SysRoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        SysUser user = userRepository.findByLoginNameAndStatus(request.loginName(), "ACTIVE")
                .orElseThrow(() -> new BusinessException("AUTH_401", "用户名或密码错误", 401));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("AUTH_401", "用户名或密码错误", 401);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        List<IdentityView> identities = loadIdentities(user.getId());
        if (identities.isEmpty()) {
            throw new BusinessException("AUTH_403", "用户无可用身份", 403);
        }

        IdentityView current = identities.stream().filter(IdentityView::isDefault).findFirst()
                .orElse(identities.get(0));
        String token = jwtService.generateToken(toContext(user, current));

        return new LoginResponse(
                token,
                user.getId(),
                user.getLoginName(),
                user.getUserName(),
                identities,
                current.identityId()
        );
    }

    public List<IdentityView> listIdentities(String userId) {
        return loadIdentities(userId);
    }

    @Transactional(readOnly = true)
    public LoginResponse switchIdentity(String userId, String identityId) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("AUTH_401", "用户不存在", 401));
        SysUserIdentity identity = identityRepository.findByIdAndUserIdAndStatus(identityId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("AUTH_403", "身份不可用", 403));

        IdentityView view = toView(identity);
        String token = jwtService.generateToken(toContext(user, view));
        List<IdentityView> identities = loadIdentities(userId);

        return new LoginResponse(
                token,
                user.getId(),
                user.getLoginName(),
                user.getUserName(),
                identities,
                identityId
        );
    }

    private List<IdentityView> loadIdentities(String userId) {
        return identityRepository.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .map(this::toView)
                .toList();
    }

    private IdentityView toView(SysUserIdentity identity) {
        SysRole role = roleRepository.findById(identity.getRoleId()).orElse(null);
        return new IdentityView(
                identity.getId(),
                identity.getOperatorId(),
                identity.getProjectId(),
                identity.getEnterpriseId(),
                identity.getRoleId(),
                role != null ? role.getRoleCode() : null,
                role != null ? role.getRoleName() : null,
                identity.getIsDefault() == 1
        );
    }

    private UserContext toContext(SysUser user, IdentityView identity) {
        return new UserContext(
                user.getId(),
                user.getLoginName(),
                identity.operatorId(),
                identity.projectId(),
                identity.enterpriseId(),
                identity.roleId(),
                identity.identityId()
        );
    }
}
