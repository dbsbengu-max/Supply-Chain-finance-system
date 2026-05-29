package com.scf.pricing.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.common.dto.PageResponse;
import com.scf.pricing.dto.*;
import com.scf.pricing.entity.MdCategory;
import com.scf.pricing.entity.MdSku;
import com.scf.pricing.service.PricingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pricing")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<MdCategory>> listCategories(HttpServletRequest request) {
        return ApiResponse.ok(pricingService.listCategories(), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/categories")
    public ApiResponse<MdCategory> createCategory(
            @Valid @RequestBody CategoryCreateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(pricingService.createCategory(body), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/skus")
    public ApiResponse<List<MdSku>> listSkus(
            @RequestParam(name = "category_id", required = false) String categoryId,
            HttpServletRequest request) {
        return ApiResponse.ok(pricingService.listSkus(categoryId), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/skus")
    public ApiResponse<MdSku> createSku(@Valid @RequestBody SkuCreateRequest body, HttpServletRequest request) {
        return ApiResponse.ok(pricingService.createSku(body), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/prices")
    public ApiResponse<PageResponse<PriceView>> listPrices(
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "sku_id", required = false) String skuId,
            @RequestParam(name = "review_status", required = false) String reviewStatus,
            HttpServletRequest request) {
        return ApiResponse.ok(pricingService.listPrices(pageNo, pageSize, skuId, reviewStatus),
                request.getHeader("X-Request-Id"));
    }

    @PostMapping("/prices")
    public ApiResponse<PriceView> createPrice(@Valid @RequestBody PriceCreateRequest body, HttpServletRequest request) {
        return ApiResponse.ok(pricingService.createPrice(body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/prices/{id}/submit")
    public ApiResponse<PriceView> submitPrice(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(pricingService.submitPrice(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/prices/{id}/approve")
    public ApiResponse<PriceView> approvePrice(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(pricingService.approvePrice(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/prices/{id}/reject")
    public ApiResponse<PriceView> rejectPrice(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(pricingService.rejectPrice(id), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/fx-rates")
    public ApiResponse<PageResponse<FxRateView>> listFxRates(
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.ok(pricingService.listFxRates(pageNo, pageSize), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/fx-rates")
    public ApiResponse<FxRateView> createFxRate(@Valid @RequestBody FxRateCreateRequest body, HttpServletRequest request) {
        return ApiResponse.ok(pricingService.createFxRate(body), request.getHeader("X-Request-Id"));
    }
}
