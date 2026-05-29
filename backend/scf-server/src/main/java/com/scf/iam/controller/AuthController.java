package com.scf.iam.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.common.security.SecurityUtils;
import com.scf.iam.dto.LoginRequest;
import com.scf.iam.dto.LoginResponse;
import com.scf.iam.dto.SwitchIdentityRequest;
import com.scf.iam.service.AuthService;
import com.scf.iam.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PermissionService permissionService;

    public AuthController(AuthService authService, PermissionService permissionService) {
        this.authService = authService;
        this.permissionService = permissionService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request);
        return ApiResponse.ok(response, httpRequest.getHeader("X-Request-Id"));
    }

    @GetMapping("/identities")
    public ApiResponse<?> identities(HttpServletRequest httpRequest) {
        var userId = SecurityUtils.currentUserId();
        return ApiResponse.ok(authService.listIdentities(userId), httpRequest.getHeader("X-Request-Id"));
    }

    @PostMapping("/switch-identity")
    public ApiResponse<LoginResponse> switchIdentity(
            @Valid @RequestBody SwitchIdentityRequest request,
            HttpServletRequest httpRequest) {
        var userId = SecurityUtils.currentUserId();
        LoginResponse response = authService.switchIdentity(userId, request.identityId());
        return ApiResponse.ok(response, httpRequest.getHeader("X-Request-Id"));
    }

    @GetMapping("/permissions")
    public ApiResponse<java.util.List<String>> permissions(HttpServletRequest httpRequest) {
        var permissions = permissionService.loadPermissions(SecurityUtils.currentUser())
                .stream()
                .sorted()
                .toList();
        return ApiResponse.ok(permissions, httpRequest.getHeader("X-Request-Id"));
    }
}
