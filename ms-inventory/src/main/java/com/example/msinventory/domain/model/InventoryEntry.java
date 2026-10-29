package com.example.msinventory.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class InventoryEntry {

    private Long id;
    private String idempotencyKey;
    private String requestHash;
    private EntryType entryType;
    private int quantity;
    private String performedBy;
    private Long purchaseId;
    private Long supplierId;
    private Instant createdAt;
}
