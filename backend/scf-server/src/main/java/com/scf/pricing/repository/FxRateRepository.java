package com.scf.pricing.repository;

import com.scf.pricing.entity.FxRate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FxRateRepository extends JpaRepository<FxRate, String> {

    Page<FxRate> findAllByOrderByRateDateDesc(Pageable pageable);
}
