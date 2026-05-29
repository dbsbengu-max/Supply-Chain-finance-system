package com.scf.idempotency.repository;

import com.scf.idempotency.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
    Optional<IdempotencyRecord> findByIdempotencyKeyAndRequestHash(String idempotencyKey, String requestHash);

    Optional<IdempotencyRecord> findFirstByIdempotencyKey(String idempotencyKey);
}
