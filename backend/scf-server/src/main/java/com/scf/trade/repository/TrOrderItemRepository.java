package com.scf.trade.repository;

import com.scf.trade.entity.TrOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrOrderItemRepository extends JpaRepository<TrOrderItem, String> {

    List<TrOrderItem> findByOrderId(String orderId);

    void deleteByOrderId(String orderId);
}
