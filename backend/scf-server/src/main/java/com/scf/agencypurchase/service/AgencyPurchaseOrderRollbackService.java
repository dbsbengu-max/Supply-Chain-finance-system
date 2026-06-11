package com.scf.agencypurchase.service;

import com.scf.agencypurchase.entity.ApAgencyPurchaseApplication;
import com.scf.audit.service.AuditLogService;
import com.scf.common.exception.BusinessException;
import com.scf.trade.entity.TrOrder;
import com.scf.trade.repository.TrOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class AgencyPurchaseOrderRollbackService {

    private final TrOrderRepository orderRepository;
    private final AuditLogService auditLogService;

    public AgencyPurchaseOrderRollbackService(
            TrOrderRepository orderRepository,
            AuditLogService auditLogService) {
        this.orderRepository = orderRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void rollbackConfirmedOrder(ApAgencyPurchaseApplication app, String orderId) {
        TrOrder order = orderRepository
                .findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
                        orderId, app.getOperatorId(), app.getProjectId(), (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "关联订单不存在", 404));
        if ("SUBMITTED".equals(order.getOrderStatus())) {
            return;
        }
        if (!"CONFIRMED".equals(order.getOrderStatus())) {
            throw new BusinessException(
                    "STATE_409", "订单状态不可回滚: " + order.getOrderStatus(), 409);
        }
        Map<String, Object> before = Map.of("order_status", order.getOrderStatus());
        order.setOrderStatus("SUBMITTED");
        order.setSignedAt(null);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        auditLogService.logAsSystem(
                "system", app.getOperatorId(), app.getProjectId(), null,
                "ORDER_ROLLBACK", "TRADE_ORDER", order.getId(),
                before,
                Map.of("order_status", "SUBMITTED", "source", "SAGA_COMPENSATION"));
    }
}
