package com.scf.customer.service;

import com.scf.audit.service.AuditLogService;
import com.scf.bpm.service.BpmProcessService;
import com.scf.common.dto.PageResponse;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.FieldMaskService;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.customer.dto.*;
import com.scf.customer.entity.MdBankAccount;
import com.scf.customer.entity.MdEnterprise;
import com.scf.customer.entity.MdEnterpriseCert;
import com.scf.customer.repository.MdBankAccountRepository;
import com.scf.customer.repository.MdEnterpriseCertRepository;
import com.scf.customer.repository.MdEnterpriseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class EnterpriseService {

    private static final String KYC_BUSINESS_TYPE = "ENTERPRISE_KYC";
    private static final String KYC_ASSIGNEE = "U001";

    private final MdEnterpriseRepository enterpriseRepository;
    private final MdBankAccountRepository bankAccountRepository;
    private final MdEnterpriseCertRepository certRepository;
    private final BpmProcessService bpmProcessService;
    private final AuditLogService auditLogService;
    private final TenantContext tenantContext;
    private final DataScopeHelper dataScopeHelper;
    private final FieldMaskService fieldMaskService;

    public EnterpriseService(
            MdEnterpriseRepository enterpriseRepository,
            MdBankAccountRepository bankAccountRepository,
            MdEnterpriseCertRepository certRepository,
            @Lazy BpmProcessService bpmProcessService,
            AuditLogService auditLogService,
            TenantContext tenantContext,
            DataScopeHelper dataScopeHelper,
            FieldMaskService fieldMaskService) {
        this.enterpriseRepository = enterpriseRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.certRepository = certRepository;
        this.bpmProcessService = bpmProcessService;
        this.auditLogService = auditLogService;
        this.tenantContext = tenantContext;
        this.dataScopeHelper = dataScopeHelper;
        this.fieldMaskService = fieldMaskService;
    }

    public PageResponse<EnterpriseView> list(int pageNo, int pageSize, String enterpriseType, String kycStatus) {
        tenantContext.requirePermission("CUSTOMER_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        UserContext user = SecurityUtils.currentUser();
        PageRequest pageable = PageRequest.of(Math.max(pageNo - 1, 0), Math.max(pageSize, 1));

        Page<MdEnterprise> page;
        if (dataScopeHelper.customerScope(user) == DataScopeHelper.ScopeType.ENTERPRISE) {
            MdEnterprise own = loadAccessible(user, user.enterpriseId());
            return PageResponse.of(pageNo, pageSize, 1, List.of(toEnterpriseView(own)));
        }
        if (dataScopeHelper.customerScope(user) == DataScopeHelper.ScopeType.NONE) {
            throw new BusinessException("AUTH_403", "无客户数据范围", 403);
        }
        if (enterpriseType != null && !enterpriseType.isBlank()) {
            page = enterpriseRepository.findByOperatorIdAndEnterpriseTypeAndDeletedFlag(operatorId, enterpriseType, (short) 0, pageable);
        } else if (kycStatus != null && !kycStatus.isBlank()) {
            page = enterpriseRepository.findByOperatorIdAndKycStatusAndDeletedFlag(operatorId, kycStatus, (short) 0, pageable);
        } else {
            page = enterpriseRepository.findByOperatorIdAndDeletedFlag(operatorId, (short) 0, pageable);
        }
        return PageResponse.of(pageNo, pageSize, page.getTotalElements(),
                page.getContent().stream().map(this::toEnterpriseView).toList());
    }

    public EnterpriseView getById(String id) {
        tenantContext.requirePermission("CUSTOMER_VIEW");
        return toEnterpriseView(loadAccessible(SecurityUtils.currentUser(), id));
    }

    @Transactional
    public EnterpriseView create(EnterpriseCreateRequest request) {
        tenantContext.requirePermission("CUSTOMER_CREATE");
        String operatorId = tenantContext.requireOperatorId();
        String userId = SecurityUtils.currentUserId();

        MdEnterprise entity = new MdEnterprise();
        entity.setId(IdGenerator.nextId());
        entity.setOperatorId(operatorId);
        entity.setEnterpriseCode("ENT" + entity.getId().substring(0, 8).toUpperCase());
        entity.setEnterpriseName(request.enterpriseName());
        entity.setEnterpriseType(request.enterpriseType());
        entity.setCountryRegion(request.countryRegion());
        entity.setRegistrationNo(request.registrationNo());
        entity.setUnifiedCreditCode(request.unifiedCreditCode());
        entity.setLegalPerson(request.legalPerson());
        entity.setKycStatus("DRAFT");
        entity.setRiskLevel("MEDIUM");
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(userId);
        entity.setCreatedAt(Instant.now());
        entity.setDeletedFlag((short) 0);
        entity.setVersionNo(1);
        enterpriseRepository.save(entity);

        auditLogService.log("CUSTOMER_CREATE", "ENTERPRISE", entity.getId(), null, Map.of("name", entity.getEnterpriseName()));
        return toEnterpriseView(entity);
    }

    @Transactional
    public EnterpriseView update(String id, EnterpriseUpdateRequest request) {
        tenantContext.requirePermission("CUSTOMER_UPDATE");
        MdEnterprise entity = loadAccessible(SecurityUtils.currentUser(), id);
        if (!"DRAFT".equals(entity.getKycStatus()) && !"REJECTED".equals(entity.getKycStatus())) {
            throw new BusinessException("STATE_409", "仅草稿或驳回状态可修改", 409);
        }
        if (request.enterpriseName() != null) {
            entity.setEnterpriseName(request.enterpriseName());
        }
        if (request.registrationNo() != null) {
            entity.setRegistrationNo(request.registrationNo());
        }
        if (request.unifiedCreditCode() != null) {
            entity.setUnifiedCreditCode(request.unifiedCreditCode());
        }
        if (request.legalPerson() != null) {
            entity.setLegalPerson(request.legalPerson());
        }
        if (request.riskLevel() != null) {
            entity.setRiskLevel(request.riskLevel());
        }
        entity.setUpdatedBy(SecurityUtils.currentUserId());
        entity.setUpdatedAt(Instant.now());
        enterpriseRepository.save(entity);
        return toEnterpriseView(entity);
    }

    @Transactional
    public EnterpriseView submitKyc(String id) {
        tenantContext.requirePermission("CUSTOMER_KYC_SUBMIT");
        MdEnterprise entity = loadAccessible(SecurityUtils.currentUser(), id);
        if (!"DRAFT".equals(entity.getKycStatus()) && !"REJECTED".equals(entity.getKycStatus())) {
            throw new BusinessException("STATE_409", "当前状态不可提交 KYC", 409);
        }
        entity.setKycStatus("PENDING");
        entity.setUpdatedBy(SecurityUtils.currentUserId());
        entity.setUpdatedAt(Instant.now());
        enterpriseRepository.save(entity);

        bpmProcessService.startProcess("ENTERPRISE_KYC", KYC_BUSINESS_TYPE, entity.getId(), KYC_ASSIGNEE);
        auditLogService.log("KYC_SUBMIT", "ENTERPRISE", entity.getId(), null, Map.of());
        return toEnterpriseView(entity);
    }

    @Transactional
    public EnterpriseView approveKyc(String id) {
        tenantContext.requirePermission("CUSTOMER_KYC_APPROVE");
        MdEnterprise entity = loadForOperator(id);
        if (!"PENDING".equals(entity.getKycStatus())) {
            throw new BusinessException("STATE_409", "仅待审状态可通过", 409);
        }
        entity.setKycStatus("APPROVED");
        entity.setUpdatedBy(SecurityUtils.currentUserId());
        entity.setUpdatedAt(Instant.now());
        enterpriseRepository.save(entity);
        auditLogService.log("KYC_APPROVE", "ENTERPRISE", entity.getId(), null, Map.of());
        return toEnterpriseView(entity);
    }

    @Transactional
    public EnterpriseView rejectKyc(String id, String reason) {
        tenantContext.requirePermission("CUSTOMER_KYC_REJECT");
        MdEnterprise entity = loadForOperator(id);
        if (!"PENDING".equals(entity.getKycStatus())) {
            throw new BusinessException("STATE_409", "仅待审状态可驳回", 409);
        }
        entity.setKycStatus("REJECTED");
        entity.setUpdatedBy(SecurityUtils.currentUserId());
        entity.setUpdatedAt(Instant.now());
        enterpriseRepository.save(entity);
        auditLogService.log("KYC_REJECT", "ENTERPRISE", entity.getId(), null, Map.of("reason", reason == null ? "" : reason));
        return toEnterpriseView(entity);
    }

    public List<Map<String, Object>> listCerts(String enterpriseId) {
        tenantContext.requirePermission("CUSTOMER_CERT_VIEW");
        loadAccessible(SecurityUtils.currentUser(), enterpriseId);
        return certRepository.findByEnterpriseIdAndDeletedFlag(enterpriseId, (short) 0).stream()
                .map(this::toCertView)
                .toList();
    }

    @Transactional
    public Map<String, Object> addCert(String enterpriseId, CertCreateRequest request) {
        tenantContext.requirePermission("CUSTOMER_CERT_UPLOAD");
        loadAccessible(SecurityUtils.currentUser(), enterpriseId);
        MdEnterpriseCert cert = new MdEnterpriseCert();
        cert.setId(IdGenerator.nextId());
        cert.setEnterpriseId(enterpriseId);
        cert.setCertType(request.certType());
        cert.setCertNo(request.certNo());
        cert.setFileId(request.fileId());
        cert.setOcrStatus("PENDING");
        cert.setCreatedBy(SecurityUtils.currentUserId());
        cert.setCreatedAt(Instant.now());
        cert.setDeletedFlag((short) 0);
        certRepository.save(cert);
        return toCertView(cert);
    }

    public List<Map<String, Object>> listBankAccounts(String enterpriseId) {
        tenantContext.requirePermission("CUSTOMER_ACCOUNT_VIEW");
        loadAccessible(SecurityUtils.currentUser(), enterpriseId);
        return bankAccountRepository.findByEnterpriseIdAndDeletedFlag(enterpriseId, (short) 0).stream()
                .map(this::toAccountView)
                .toList();
    }

    @Transactional
    public Map<String, Object> addBankAccount(String enterpriseId, BankAccountCreateRequest request) {
        tenantContext.requirePermission("CUSTOMER_ACCOUNT_CREATE");
        loadAccessible(SecurityUtils.currentUser(), enterpriseId);
        MdBankAccount account = new MdBankAccount();
        account.setId(IdGenerator.nextId());
        account.setEnterpriseId(enterpriseId);
        account.setAccountType(request.accountType());
        account.setBankName(request.bankName());
        account.setAccountName(request.accountName());
        account.setAccountNo(request.accountNo());
        account.setCurrency(request.currency());
        account.setVerificationStatus("PENDING");
        account.setIsRepaymentAccount(Boolean.TRUE.equals(request.isRepaymentAccount()) ? (short) 1 : (short) 0);
        account.setCreatedBy(SecurityUtils.currentUserId());
        account.setCreatedAt(Instant.now());
        account.setDeletedFlag((short) 0);
        bankAccountRepository.save(account);
        return toAccountView(account);
    }

    @Transactional
    public void updateKycStatusFromBpm(String enterpriseId, String kycStatus) {
        MdEnterprise entity = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new BusinessException("DATA_404", "企业不存在", 404));
        entity.setKycStatus(kycStatus);
        entity.setUpdatedAt(Instant.now());
        enterpriseRepository.save(entity);
    }

    private MdEnterprise loadAccessible(UserContext user, String id) {
        String operatorId = tenantContext.requireOperatorId();
        MdEnterprise entity = enterpriseRepository.findByIdAndOperatorIdAndDeletedFlag(id, operatorId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "企业不存在", 404));
        if (!dataScopeHelper.canAccessEnterprise(user, entity.getId())) {
            throw new BusinessException("AUTH_403", "无权访问该企业", 403);
        }
        return entity;
    }

    private MdEnterprise loadForOperator(String id) {
        String operatorId = tenantContext.requireOperatorId();
        return enterpriseRepository.findByIdAndOperatorIdAndDeletedFlag(id, operatorId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "企业不存在", 404));
    }

    private Map<String, Object> toCertView(MdEnterpriseCert cert) {
        return Map.of(
                "id", cert.getId(),
                "cert_type", cert.getCertType(),
                "cert_no", safeValue(fieldMaskService.apply("ENTERPRISE_CERT", "cert_no", cert.getCertNo())),
                "file_id", cert.getFileId(),
                "ocr_status", cert.getOcrStatus());
    }

    private Map<String, Object> toAccountView(MdBankAccount account) {
        return Map.of(
                "id", account.getId(),
                "account_type", account.getAccountType(),
                "bank_name", account.getBankName(),
                "account_name", account.getAccountName(),
                "account_no", safeValue(fieldMaskService.apply("BANK_ACCOUNT", "account_no", account.getAccountNo())),
                "currency", account.getCurrency(),
                "verification_status", account.getVerificationStatus());
    }

    private EnterpriseView toEnterpriseView(MdEnterprise entity) {
        EnterpriseView view = EnterpriseView.from(entity);
        return new EnterpriseView(
                view.id(),
                view.operatorId(),
                view.enterpriseCode(),
                view.enterpriseName(),
                view.enterpriseType(),
                view.countryRegion(),
                view.registrationNo(),
                fieldMaskService.apply("ENTERPRISE", "unified_credit_code", view.unifiedCreditCode()),
                fieldMaskService.apply("ENTERPRISE", "legal_person", view.legalPerson()),
                view.kycStatus(),
                view.riskLevel(),
                view.status(),
                view.createdAt());
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
