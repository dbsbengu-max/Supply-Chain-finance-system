package com.scf.agencypurchase.service;

import com.scf.agencypurchase.AgencyPurchaseModeCatalog;
import com.scf.agencypurchase.dto.AgencyPurchaseCreateRequest;
import com.scf.agencypurchase.dto.AgencyPurchaseView;
import com.scf.agencypurchase.entity.ApAgencyPurchaseApplication;
import com.scf.agencypurchase.repository.ApAgencyPurchaseApplicationRepository;
import com.scf.audit.service.AuditLogService;
import com.scf.bpm.entity.BpmProcessInstance;
import com.scf.bpm.service.BpmProcessService;
import com.scf.common.dto.PageResponse;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

@Service
public class AgencyPurchaseApplicationService {

    private static final Set<String> CANCELLABLE = Set.of("DRAFT", "SUBMITTED", "REVIEWING");

    private static final Instant FILTER_DATE_MIN = Instant.EPOCH;
    private static final Instant FILTER_DATE_MAX = Instant.parse("9999-12-31T23:59:59Z");

    private final ApAgencyPurchaseApplicationRepository repository;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;
    private final DataScopeHelper dataScopeHelper;
    private final BpmProcessService bpmProcessService;

    public AgencyPurchaseApplicationService(
            ApAgencyPurchaseApplicationRepository repository,
            TenantContext tenantContext,
            AuditLogService auditLogService,
            DataScopeHelper dataScopeHelper,
            BpmProcessService bpmProcessService) {
        this.repository = repository;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
        this.dataScopeHelper = dataScopeHelper;
        this.bpmProcessService = bpmProcessService;
    }

    public PageResponse<AgencyPurchaseView> list(
            int pageNo,
            int pageSize,
            String applicationStatus,
            String orderMode,
            String fundSource,
            String pickupType,
            String customerId,
            String createdFrom,
            String createdTo) {
        tenantContext.requirePermission("AGENCY_PURCHASE_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        UserContext user = SecurityUtils.currentUser();
        PageRequest pageable = PageRequest.of(Math.max(pageNo - 1, 0), Math.max(pageSize, 1));
        Instant from = parseDateStart(createdFrom);
        Instant to = parseDateEnd(createdTo);
        String statusFilter = filterOrEmpty(applicationStatus);
        String orderModeFilter = filterOrEmpty(orderMode);
        String fundSourceFilter = filterOrEmpty(fundSource);
        String pickupTypeFilter = filterOrEmpty(pickupType);
        String customerFilter = filterOrEmpty(customerId);
        Instant fromBound = from != null ? from : FILTER_DATE_MIN;
        Instant toBound = to != null ? to : FILTER_DATE_MAX;

        Page<ApAgencyPurchaseApplication> page;
        if (dataScopeHelper.isEnterpriseRole(user)) {
            String scopedCustomer = user.enterpriseId();
            page = repository.searchByCustomer(
                    operatorId, projectId, scopedCustomer,
                    statusFilter, orderModeFilter, fundSourceFilter,
                    pickupTypeFilter, fromBound, toBound, pageable);
        } else if (dataScopeHelper.canReadOperatorData(user) || dataScopeHelper.isFundingRole(user)) {
            page = repository.search(
                    operatorId, projectId,
                    statusFilter, orderModeFilter, fundSourceFilter,
                    pickupTypeFilter, customerFilter, fromBound, toBound, pageable);
        } else {
            throw new BusinessException("AUTH_403", "无代采数据范围", 403);
        }

        var records = page.getContent().stream().map(AgencyPurchaseView::from).toList();
        return PageResponse.of(pageNo, pageSize, page.getTotalElements(), records);
    }

    public AgencyPurchaseView getById(String id) {
        tenantContext.requirePermission("AGENCY_PURCHASE_VIEW");
        return AgencyPurchaseView.from(loadAccessible(id));
    }

    @Transactional
    public AgencyPurchaseView create(AgencyPurchaseCreateRequest request) {
        tenantContext.requirePermission("AGENCY_PURCHASE_CREATE");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        UserContext user = SecurityUtils.currentUser();

        AgencyPurchaseModeCatalog.ModeDefinition mode = AgencyPurchaseModeCatalog.resolve(
                request.orderMode(), request.fundSource(), request.pickupType());
        validateBusinessFields(request, mode);

        if (dataScopeHelper.isEnterpriseRole(user) && !user.enterpriseId().equals(request.customerId())) {
            throw new BusinessException("AUTH_403", "仅可为本企业创建代采申请", 403);
        }

        ApAgencyPurchaseApplication app = new ApAgencyPurchaseApplication();
        app.setId(IdGenerator.nextId());
        app.setOperatorId(operatorId);
        app.setProjectId(projectId);
        app.setApplicationNo("AGP-" + System.currentTimeMillis());
        app.setOrderMode(mode.orderMode());
        app.setFundSource(mode.fundSource());
        app.setPickupType(mode.pickupType());
        app.setModeKey(mode.modeKey());
        app.setCustomerId(request.customerId());
        app.setTradeCompanyId(request.tradeCompanyId());
        app.setOrderId(blankToNull(request.orderId()));
        app.setCurrency(request.currency());
        app.setTotalAmount(new BigDecimal(request.totalAmount()));
        app.setApplicationStatus("DRAFT");
        app.setRemark(blankToNull(request.remark()));
        app.setCreatedBy(user.userId());
        app.setCreatedAt(Instant.now());
        app.setDeletedFlag((short) 0);
        app.setVersionNo(1);
        repository.save(app);

        auditLogService.log("AGENCY_PURCHASE_CREATE", "AGENCY_PURCHASE", app.getId(), null,
                Map.of("application_no", app.getApplicationNo(), "mode_key", app.getModeKey()));
        return AgencyPurchaseView.from(app);
    }

    @Transactional
    public AgencyPurchaseView update(String id, AgencyPurchaseCreateRequest request) {
        tenantContext.requirePermission("AGENCY_PURCHASE_CREATE");
        ApAgencyPurchaseApplication app = loadAccessible(id);
        if (!"DRAFT".equals(app.getApplicationStatus())) {
            throw new BusinessException("STATE_409", "仅草稿申请可编辑", 409);
        }

        AgencyPurchaseModeCatalog.ModeDefinition mode = AgencyPurchaseModeCatalog.resolve(
                request.orderMode(), request.fundSource(), request.pickupType());
        validateBusinessFields(request, mode);

        UserContext user = SecurityUtils.currentUser();
        if (dataScopeHelper.isEnterpriseRole(user) && !user.enterpriseId().equals(request.customerId())) {
            throw new BusinessException("AUTH_403", "仅可编辑本企业代采申请", 403);
        }

        app.setOrderMode(mode.orderMode());
        app.setFundSource(mode.fundSource());
        app.setPickupType(mode.pickupType());
        app.setModeKey(mode.modeKey());
        app.setCustomerId(request.customerId());
        app.setTradeCompanyId(request.tradeCompanyId());
        app.setOrderId(blankToNull(request.orderId()));
        app.setCurrency(request.currency());
        app.setTotalAmount(new BigDecimal(request.totalAmount()));
        app.setRemark(blankToNull(request.remark()));
        app.setUpdatedBy(user.userId());
        app.setUpdatedAt(Instant.now());
        app.setVersionNo(app.getVersionNo() + 1);
        repository.save(app);

        auditLogService.log("AGENCY_PURCHASE_UPDATE", "AGENCY_PURCHASE", app.getId(), null, Map.of());
        return AgencyPurchaseView.from(app);
    }

    @Transactional
    public AgencyPurchaseView submit(String id) {
        tenantContext.requirePermission("AGENCY_PURCHASE_SUBMIT");
        ApAgencyPurchaseApplication app = loadAccessible(id);
        if (!"DRAFT".equals(app.getApplicationStatus())) {
            throw new BusinessException("STATE_409", "仅草稿申请可提交", 409);
        }

        app.setApplicationStatus("SUBMITTED");
        app.setUpdatedBy(SecurityUtils.currentUserId());
        app.setUpdatedAt(Instant.now());
        repository.save(app);

        app.setApplicationStatus("REVIEWING");
        BpmProcessInstance instance = bpmProcessService.startProcess(
                "AGENCY_PURCHASE_APPROVAL",
                "AGENCY_PURCHASE",
                app.getId(),
                SecurityUtils.currentUserId());
        app.setBpmInstanceId(instance.getId());
        app.setUpdatedAt(Instant.now());
        repository.save(app);

        auditLogService.log("AGENCY_PURCHASE_SUBMIT", "AGENCY_PURCHASE", app.getId(), null,
                Map.of("bpm_instance_id", instance.getId()));
        return AgencyPurchaseView.from(app);
    }

    @Transactional
    public AgencyPurchaseView cancel(String id) {
        tenantContext.requirePermission("AGENCY_PURCHASE_CANCEL");
        ApAgencyPurchaseApplication app = loadAccessible(id);
        if (!CANCELLABLE.contains(app.getApplicationStatus())) {
            throw new BusinessException("STATE_409", "当前状态不可取消", 409);
        }
        app.setApplicationStatus("CANCELLED");
        app.setUpdatedBy(SecurityUtils.currentUserId());
        app.setUpdatedAt(Instant.now());
        repository.save(app);
        auditLogService.log("AGENCY_PURCHASE_CANCEL", "AGENCY_PURCHASE", app.getId(), null, Map.of());
        return AgencyPurchaseView.from(app);
    }

    private ApAgencyPurchaseApplication loadAccessible(String id) {
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        ApAgencyPurchaseApplication app = repository.findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
                        id, operatorId, projectId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "代采申请不存在", 404));
        UserContext user = SecurityUtils.currentUser();
        if (dataScopeHelper.isEnterpriseRole(user)
                && !user.enterpriseId().equals(app.getCustomerId())
                && !user.enterpriseId().equals(app.getTradeCompanyId())) {
            throw new BusinessException("AUTH_403", "无权访问该代采申请", 403);
        }
        return app;
    }

    private void validateBusinessFields(
            AgencyPurchaseCreateRequest request,
            AgencyPurchaseModeCatalog.ModeDefinition mode) {
        if ("STOCK_ORDER".equals(mode.orderMode())
                && (request.orderId() == null || request.orderId().isBlank())) {
            throw new BusinessException("VALID_400", "订单模式下 order_id 必填", 400);
        }
        if (new BigDecimal(request.totalAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("VALID_400", "total_amount 必须大于 0", 400);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String filterOrEmpty(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? "" : trimmed;
    }

    private static Instant parseDateStart(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static Instant parseDateEnd(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date).plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC);
    }
}
