package com.scf.voucher.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.common.dto.PageResponse;
import com.scf.voucher.dto.VoucherDtos.VoucherCreateRequest;
import com.scf.voucher.dto.VoucherDtos.VoucherDetailView;
import com.scf.voucher.dto.VoucherDtos.VoucherRedeemRequest;
import com.scf.voucher.dto.VoucherDtos.VoucherSplitRequest;
import com.scf.voucher.dto.VoucherDtos.VoucherTransferRequest;
import com.scf.voucher.dto.VoucherDtos.VoucherView;
import com.scf.voucher.service.VoucherService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/dv/vouchers", "/vouchers"})
public class VoucherController {

    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping
    public ApiResponse<PageResponse<VoucherView>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        return ApiResponse.ok(voucherService.list(pageNo, pageSize, status), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/{id}")
    public ApiResponse<VoucherDetailView> get(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(voucherService.get(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping
    public ApiResponse<VoucherDetailView> create(
            @Valid @RequestBody VoucherCreateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(voucherService.create(body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/issue")
    public ApiResponse<VoucherDetailView> issue(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(voucherService.issue(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<VoucherDetailView> transfer(
            @PathVariable String id,
            @Valid @RequestBody VoucherTransferRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(voucherService.transfer(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/split")
    public ApiResponse<VoucherDetailView> split(
            @PathVariable String id,
            @Valid @RequestBody VoucherSplitRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(voucherService.split(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/redeem-apply")
    public ApiResponse<VoucherDetailView> redeemApply(
            @PathVariable String id,
            @RequestBody(required = false) VoucherRedeemRequest body,
            HttpServletRequest request) {
        VoucherRedeemRequest effectiveBody = body == null ? new VoucherRedeemRequest(null) : body;
        return ApiResponse.ok(voucherService.redeemApply(id, effectiveBody), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<VoucherDetailView> cancel(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(voucherService.cancel(id), request.getHeader("X-Request-Id"));
    }
}
