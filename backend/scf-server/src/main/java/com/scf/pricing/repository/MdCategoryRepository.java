package com.scf.pricing.repository;

import com.scf.pricing.entity.MdCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MdCategoryRepository extends JpaRepository<MdCategory, String> {

    List<MdCategory> findByStatusOrderByCategoryCode(String status);
}
