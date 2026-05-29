package com.scf.pricing.service;

import com.scf.audit.service.AuditLogService;
import com.scf.common.dto.PageResponse;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.util.IdGenerator;
import com.scf.pricing.dto.*;
import com.scf.pricing.entity.*;
import com.scf.pricing.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class PricingService {

    private final MdCategoryRepository categoryRepository;
    private final MdSkuRepository skuRepository;
    private final PrPriceRecordRepository priceRepository;
    private final FxRateRepository fxRateRepository;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;

    public PricingService(
            MdCategoryRepository categoryRepository,
            MdSkuRepository skuRepository,
            PrPriceRecordRepository priceRepository,
            FxRateRepository fxRateRepository,
            TenantContext tenantContext,
            AuditLogService auditLogService) {
        this.categoryRepository = categoryRepository;
        this.skuRepository = skuRepository;
        this.priceRepository = priceRepository;
        this.fxRateRepository = fxRateRepository;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
    }

    public List<MdCategory> listCategories() {
        tenantContext.requirePermission("PRICE_VIEW");
        return categoryRepository.findByStatusOrderByCategoryCode("ENABLED");
    }

    @Transactional
    public MdCategory createCategory(CategoryCreateRequest request) {
        tenantContext.requirePermission("PRICE_CATEGORY_CREATE");
        MdCategory category = new MdCategory();
        category.setId(IdGenerator.nextId());
        category.setCategoryCode(request.categoryCode());
        category.setCategoryName(request.categoryName());
        category.setCategoryType(request.categoryType());
        category.setDefaultUnit(request.defaultUnit());
        category.setStatus("ENABLED");
        return categoryRepository.save(category);
    }

    public List<MdSku> listSkus(String categoryId) {
        tenantContext.requirePermission("PRICE_VIEW");
        if (categoryId != null && !categoryId.isBlank()) {
            return skuRepository.findByCategoryIdAndStatus(categoryId, "ENABLED");
        }
        return skuRepository.findByStatus("ENABLED");
    }

    @Transactional
    public MdSku createSku(SkuCreateRequest request) {
        tenantContext.requirePermission("PRICE_SKU_CREATE");
        categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BusinessException("DATA_404", "品类不存在", 404));
        MdSku sku = new MdSku();
        sku.setId(IdGenerator.nextId());
        sku.setCategoryId(request.categoryId());
        sku.setSkuCode(request.skuCode());
        sku.setSpec(request.spec());
        sku.setGrade(request.grade());
        sku.setOrigin(request.origin());
        sku.setPackageType(request.packageType());
        sku.setUnit(request.unit());
        sku.setStatus("ENABLED");
        return skuRepository.save(sku);
    }

    public PageResponse<PriceView> listPrices(int pageNo, int pageSize, String skuId, String reviewStatus) {
        tenantContext.requirePermission("PRICE_VIEW");
        PageRequest pageable = PageRequest.of(Math.max(pageNo - 1, 0), Math.max(pageSize, 1));
        Page<PrPriceRecord> page;
        if (skuId != null && !skuId.isBlank()) {
            page = priceRepository.findBySkuIdOrderByPriceDateDesc(skuId, pageable);
        } else if (reviewStatus != null && !reviewStatus.isBlank()) {
            page = priceRepository.findByReviewStatusOrderByPriceDateDesc(reviewStatus, pageable);
        } else {
            page = priceRepository.findAllByOrderByPriceDateDesc(pageable);
        }
        return PageResponse.of(pageNo, pageSize, page.getTotalElements(),
                page.getContent().stream().map(PriceView::from).toList());
    }

    @Transactional
    public PriceView createPrice(PriceCreateRequest request) {
        tenantContext.requirePermission("PRICE_CREATE");
        skuRepository.findById(request.skuId())
                .orElseThrow(() -> new BusinessException("DATA_404", "SKU 不存在", 404));
        PrPriceRecord record = new PrPriceRecord();
        record.setId(IdGenerator.nextId());
        record.setSkuId(request.skuId());
        record.setPriceDate(request.priceDate());
        record.setPrice(new BigDecimal(request.price()));
        record.setCurrency(request.currency());
        record.setUnit(request.unit());
        record.setSourceType(request.sourceType());
        record.setSourceName(request.sourceName());
        record.setTrustLevel(request.trustLevel());
        record.setReviewStatus("DRAFT");
        record.setVersionNo(1);
        record.setAbnormalFlag((short) 0);
        record.setCreatedBy(SecurityUtils.currentUserId());
        record.setCreatedAt(Instant.now());
        priceRepository.save(record);
        auditLogService.log("PRICE_CREATE", "PRICE", record.getId(), null, Map.of("sku_id", record.getSkuId()));
        return PriceView.from(record);
    }

    @Transactional
    public PriceView submitPrice(String id) {
        tenantContext.requirePermission("PRICE_SUBMIT");
        PrPriceRecord record = loadPrice(id);
        if (!"DRAFT".equals(record.getReviewStatus()) && !"REJECTED".equals(record.getReviewStatus())) {
            throw new BusinessException("STATE_409", "当前状态不可提交审核", 409);
        }
        record.setReviewStatus("PENDING");
        priceRepository.save(record);
        return PriceView.from(record);
    }

    @Transactional
    public PriceView approvePrice(String id) {
        tenantContext.requirePermission("PRICE_APPROVE");
        PrPriceRecord record = loadPrice(id);
        if (!"PENDING".equals(record.getReviewStatus())) {
            throw new BusinessException("STATE_409", "仅待审价格可通过", 409);
        }
        record.setReviewStatus("APPROVED");
        record.setApprovedBy(SecurityUtils.currentUserId());
        record.setApprovedAt(Instant.now());
        priceRepository.save(record);
        auditLogService.log("PRICE_APPROVE", "PRICE", record.getId(), null, Map.of());
        return PriceView.from(record);
    }

    @Transactional
    public PriceView rejectPrice(String id) {
        tenantContext.requirePermission("PRICE_REJECT");
        PrPriceRecord record = loadPrice(id);
        if (!"PENDING".equals(record.getReviewStatus())) {
            throw new BusinessException("STATE_409", "仅待审价格可驳回", 409);
        }
        record.setReviewStatus("REJECTED");
        priceRepository.save(record);
        return PriceView.from(record);
    }

    public PageResponse<FxRateView> listFxRates(int pageNo, int pageSize) {
        tenantContext.requirePermission("PRICE_VIEW");
        PageRequest pageable = PageRequest.of(Math.max(pageNo - 1, 0), Math.max(pageSize, 1));
        Page<FxRate> page = fxRateRepository.findAllByOrderByRateDateDesc(pageable);
        return PageResponse.of(pageNo, pageSize, page.getTotalElements(),
                page.getContent().stream().map(FxRateView::from).toList());
    }

    @Transactional
    public FxRateView createFxRate(FxRateCreateRequest request) {
        tenantContext.requirePermission("PRICE_CREATE");
        FxRate rate = new FxRate();
        rate.setId(IdGenerator.nextId());
        rate.setBaseCurrency(request.baseCurrency());
        rate.setQuoteCurrency(request.quoteCurrency());
        rate.setRate(new BigDecimal(request.rate()));
        rate.setRateDate(request.rateDate());
        rate.setSourceType(request.sourceType());
        rate.setSourceName(request.sourceName());
        rate.setReviewStatus("DRAFT");
        rate.setVersionNo(1);
        rate.setCreatedBy(SecurityUtils.currentUserId());
        rate.setCreatedAt(Instant.now());
        fxRateRepository.save(rate);
        return FxRateView.from(rate);
    }

    private PrPriceRecord loadPrice(String id) {
        return priceRepository.findById(id)
                .orElseThrow(() -> new BusinessException("DATA_404", "价格记录不存在", 404));
    }
}
