package com.scf.trade.repository;

import com.scf.trade.entity.TrDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrDocumentRepository extends JpaRepository<TrDocument, String> {

    List<TrDocument> findByBusinessTypeAndBusinessIdAndDeletedFlag(
            String businessType, String businessId, short deletedFlag);
}
