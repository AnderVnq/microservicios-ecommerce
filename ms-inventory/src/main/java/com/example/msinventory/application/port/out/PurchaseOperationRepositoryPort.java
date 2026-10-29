package com.example.msinventory.application.port.out;

import com.example.msinventory.domain.model.PurchaseOperation;

import java.util.Optional;

public interface PurchaseOperationRepositoryPort {

    PurchaseOperation save(PurchaseOperation operation);

    Optional<PurchaseOperation> findByIdempotencyKey(String idempotencyKey);
}
