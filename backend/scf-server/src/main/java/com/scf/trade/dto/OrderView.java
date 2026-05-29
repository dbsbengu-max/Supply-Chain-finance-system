package com.scf.trade.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.trade.entity.TrOrder;
import com.scf.trade.entity.TrOrderItem;

import java.time.Instant;
import java.util.List;

public record OrderView(
        String id,
        @JsonProperty("order_no") String orderNo,
        @JsonProperty("order_type") String orderType,
        @JsonProperty("buyer_id") String buyerId,
        @JsonProperty("seller_id") String sellerId,
        @JsonProperty("trade_company_id") String tradeCompanyId,
        @JsonProperty("total_amount") String totalAmount,
        String currency,
        @JsonProperty("country_from") String countryFrom,
        @JsonProperty("country_to") String countryTo,
        @JsonProperty("order_status") String orderStatus,
        @JsonProperty("created_at") Instant createdAt,
        List<OrderItemView> items
) {
    public static OrderView from(TrOrder order, List<TrOrderItem> items) {
        return new OrderView(
                order.getId(), order.getOrderNo(), order.getOrderType(),
                order.getBuyerId(), order.getSellerId(), order.getTradeCompanyId(),
                order.getTotalAmount().toPlainString(), order.getCurrency(),
                order.getCountryFrom(), order.getCountryTo(), order.getOrderStatus(),
                order.getCreatedAt(),
                items.stream().map(OrderItemView::from).toList());
    }

    public static OrderView fromSummary(TrOrder order) {
        return new OrderView(
                order.getId(), order.getOrderNo(), order.getOrderType(),
                order.getBuyerId(), order.getSellerId(), order.getTradeCompanyId(),
                order.getTotalAmount().toPlainString(), order.getCurrency(),
                order.getCountryFrom(), order.getCountryTo(), order.getOrderStatus(),
                order.getCreatedAt(), List.of());
    }
}
