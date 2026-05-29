package com.scf.pricing.repository;

import com.scf.pricing.entity.MdSku;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MdSkuRepository extends JpaRepository<MdSku, String> {

    List<MdSku> findByCategoryIdAndStatus(String categoryId, String status);

    List<MdSku> findByStatus(String status);
}
