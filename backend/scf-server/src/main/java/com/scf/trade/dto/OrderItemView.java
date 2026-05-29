package com.scf.trade.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.trade.entity.TrOrderItem;

import java.time.LocalDate;

public record OrderItemView(
        String id,
        @JsonProperty("sku_id") String skuId,
        String quantity,
        String unit,
        @JsonProperty("unit_price") String unitPrice,
        String amount,
        @JsonProperty("delivery_date") LocalDate deliveryDate
) {
    public static OrderItemView from(TrOrderItem item) {
        return new OrderItemView(
                item.getId(), item.getSkuId(),
                item.getQuantity().toPlainString(), item.getUnit(),
                item.getUnitPrice().toPlainString(), item.getAmount().toPlainString(),
                item.getDeliveryDate());
    }
}
