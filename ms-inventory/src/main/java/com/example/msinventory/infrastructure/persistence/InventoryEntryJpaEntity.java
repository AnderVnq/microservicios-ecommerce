package com.example.msinventory.infrastructure.persistence;

import com.example.msinventory.domain.model.EntryType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "inventory_entries", uniqueConstraints =
        @UniqueConstraint(name = "uk_inventory_entries_idempotency_key", columnNames = "idempotency_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryEntryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(name = "entry_type", nullable = false, length = 40)
    private EntryType entryType;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
