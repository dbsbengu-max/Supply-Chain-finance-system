package com.scf.trade.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.common.dto.PageResponse;
import com.scf.trade.dto.DocumentCreateRequest;
import com.scf.trade.dto.OrderCreateRequest;
import com.scf.trade.dto.OrderView;
import com.scf.trade.service.TradeOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/trade")
public class TradeOrderController {

    private final TradeOrderService tradeOrderService;

    public TradeOrderController(TradeOrderService tradeOrderService) {
        this.tradeOrderService = tradeOrderService;
    }

    @GetMapping("/orders")
    public ApiResponse<PageResponse<OrderView>> list(
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "order_status", required = false) String orderStatus,
            HttpServletRequest request) {
        return ApiResponse.ok(tradeOrderService.list(pageNo, pageSize, orderStatus),
                request.getHeader("X-Request-Id"));
    }

    @PostMapping("/orders")
    public ApiResponse<OrderView> create(@Valid @RequestBody OrderCreateRequest body, HttpServletRequest request) {
        return ApiResponse.ok(tradeOrderService.create(body), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<OrderView> get(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(tradeOrderService.getById(id), request.getHeader("X-Request-Id"));
    }

    @PutMapping("/orders/{id}")
    public ApiResponse<OrderView> update(
            @PathVariable String id,
            @Valid @RequestBody OrderCreateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(tradeOrderService.update(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/orders/{id}/submit")
    public ApiResponse<OrderView> submit(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(tradeOrderService.submit(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/orders/{id}/confirm")
    public ApiResponse<OrderView> confirm(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(tradeOrderService.confirm(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<OrderView> cancel(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(tradeOrderService.cancel(id), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/orders/{id}/documents")
    public ApiResponse<List<Map<String, Object>>> listDocuments(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(tradeOrderService.listDocuments(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/orders/{id}/documents")
    public ApiResponse<Map<String, Object>> addDocument(
            @PathVariable String id,
            @Valid @RequestBody DocumentCreateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(tradeOrderService.addDocument(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/documents/{id}/ocr")
    public ApiResponse<Map<String, Object>> ocrDocument(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(tradeOrderService.ocrDocument(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/orders/{id}/validate-background")
    public ApiResponse<Map<String, Object>> validateBackground(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(tradeOrderService.validateBackground(id), request.getHeader("X-Request-Id"));
    }
}
