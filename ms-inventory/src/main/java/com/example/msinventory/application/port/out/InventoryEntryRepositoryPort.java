package com.example.msinventory.application.port.out;

import com.example.msinventory.domain.model.InventoryEntry;

import java.util.Optional;

public interface InventoryEntryRepositoryPort {

    InventoryEntry save(InventoryEntry entry);

    Optional<InventoryEntry> findByIdempotencyKey(String idempotencyKey);

    /** La factura vive en las unidades: true si alguna unidad ya tiene ese invoice_number. */
    boolean existsOtherWithInvoice(String invoiceNumber);
}
