package com.example.msinventory.infrastructure.persistence;

import com.example.msinventory.domain.model.PurchaseOperationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;

import java.time.Instant;

@Entity
@Table(name = "purchase_operations", uniqueConstraints =
        @UniqueConstraint(name = "uk_purchase_operations_idempotency_key", columnNames = "idempotency_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOperationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(name = "type", nullable = false, length = 40)
    private PurchaseOperationType type;

    /** unitIds separados por coma. */
    @Column(name = "unit_ids", nullable = false, columnDefinition = "text")
    private String unitIds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
