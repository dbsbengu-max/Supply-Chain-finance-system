package com.scf.audit.service;

import com.scf.audit.entity.AuditOperationLog;
import com.scf.audit.repository.AuditOperationLogRepository;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuditLogService {

    private final AuditOperationLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditOperationLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void log(String action, String objectType, String objectId, Object before, Object after) {
        UserContext user = SecurityUtils.currentUser();
        logAsSystem(
                user.userId(),
                user.operatorId(),
                user.enterpriseId(),
                user.projectId(),
                action,
                objectType,
                objectId,
                before,
                after);
    }

    @Transactional
    public void logAsSystem(
            String userId,
            String operatorId,
            String enterpriseId,
            String projectId,
            String action,
            String objectType,
            String objectId,
            Object before,
            Object after) {
        AuditOperationLog log = new AuditOperationLog();
        log.setId(IdGenerator.nextId());
        log.setUserId(userId);
        log.setOperatorId(operatorId);
        log.setEnterpriseId(enterpriseId);
        log.setProjectId(projectId);
        log.setAction(action);
        log.setObjectType(objectType);
        log.setObjectId(objectId);
        log.setBeforeValue(toJson(before));
        log.setAfterValue(toJson(after));
        log.setIpAddress(clientIp());
        log.setOperationAt(java.time.Instant.now());
        repository.save(log);
    }

    private String clientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
