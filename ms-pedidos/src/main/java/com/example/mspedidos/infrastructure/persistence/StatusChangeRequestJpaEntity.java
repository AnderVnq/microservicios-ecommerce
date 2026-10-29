package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.StatusChangeRequestType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;

import java.time.Instant;

@Entity
@Table(name = "status_change_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_status_change_requests_key", columnNames = "idempotency_key"),
        indexes = {
        @Index(name = "idx_status_change_requests_item", columnList = "order_item_id"),
        @Index(name = "idx_status_change_requests_purchase", columnList = "purchase_id"),
        @Index(name = "idx_status_change_requests_status", columnList = "status")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusChangeRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(nullable = false, length = 40)
    private StatusChangeRequestType type;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "purchase_id")
    private Long purchaseId;

    @Column(name = "inventory_unit_id")
    private Long inventoryUnitId;

    @Column
    private Integer quantity;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(nullable = false, length = 40)
    private StatusChangeRequestStatus status;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Solo "no recibido"; null en los demas tipos. */
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;
}
