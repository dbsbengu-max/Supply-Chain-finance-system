package com.scf.project.service;

import com.scf.audit.service.AuditLogService;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.util.IdGenerator;
import com.scf.project.dto.ProjectCreateRequest;
import com.scf.project.dto.ProjectUpdateRequest;
import com.scf.project.dto.ProjectView;
import com.scf.project.entity.SysProject;
import com.scf.project.repository.SysProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ProjectService {

    private final SysProjectRepository projectRepository;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;

    public ProjectService(SysProjectRepository projectRepository, TenantContext tenantContext, AuditLogService auditLogService) {
        this.projectRepository = projectRepository;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
    }

    public List<ProjectView> list() {
        tenantContext.requirePermission("PROJECT_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        return projectRepository.findByOperatorIdAndDeletedFlagOrderByCreatedAtDesc(operatorId, (short) 0)
                .stream().map(ProjectView::from).toList();
    }

    public ProjectView get(String id) {
        tenantContext.requirePermission("PROJECT_VIEW");
        return ProjectView.from(load(id));
    }

    @Transactional
    public ProjectView create(ProjectCreateRequest request) {
        tenantContext.requirePermission("PROJECT_CREATE");
        String operatorId = tenantContext.requireOperatorId();
        if (projectRepository.existsByOperatorIdAndProjectCodeAndDeletedFlag(operatorId, request.projectCode(), (short) 0)) {
            throw new BusinessException("DATA_409", "项目编码已存在", 409);
        }
        SysProject project = new SysProject();
        project.setId(IdGenerator.nextId());
        project.setOperatorId(operatorId);
        project.setProjectCode(request.projectCode());
        project.setProjectName(request.projectName());
        project.setCountries(request.countries());
        project.setCurrencies(request.currencies());
        project.setStatus("ACTIVE");
        project.setCreatedBy(SecurityUtils.currentUserId());
        project.setCreatedAt(Instant.now());
        project.setDeletedFlag((short) 0);
        project.setVersionNo(1);
        projectRepository.save(project);
        auditLogService.log("PROJECT_CREATE", "PROJECT", project.getId(), null, Map.of("code", project.getProjectCode()));
        return ProjectView.from(project);
    }

    @Transactional
    public ProjectView update(String id, ProjectUpdateRequest request) {
        tenantContext.requirePermission("PROJECT_UPDATE");
        SysProject project = load(id);
        if (request.projectName() != null) {
            project.setProjectName(request.projectName());
        }
        if (request.countries() != null) {
            project.setCountries(request.countries());
        }
        if (request.currencies() != null) {
            project.setCurrencies(request.currencies());
        }
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        project.setUpdatedBy(SecurityUtils.currentUserId());
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        return ProjectView.from(project);
    }

    private SysProject load(String id) {
        String operatorId = tenantContext.requireOperatorId();
        return projectRepository.findByIdAndOperatorIdAndDeletedFlag(id, operatorId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "项目不存在", 404));
    }
}
