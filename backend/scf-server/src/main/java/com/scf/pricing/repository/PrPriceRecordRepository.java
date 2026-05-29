package com.scf.pricing.repository;

import com.scf.pricing.entity.PrPriceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrPriceRecordRepository extends JpaRepository<PrPriceRecord, String> {

    Page<PrPriceRecord> findBySkuIdOrderByPriceDateDesc(String skuId, Pageable pageable);

    Page<PrPriceRecord> findByReviewStatusOrderByPriceDateDesc(String reviewStatus, Pageable pageable);

    Page<PrPriceRecord> findAllByOrderByPriceDateDesc(Pageable pageable);
}
