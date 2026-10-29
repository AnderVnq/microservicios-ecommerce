package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.domain.model.UndoRequestStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;

import java.time.Instant;

@Entity
@Table(name = "allocation_undo_requests", indexes = {
        @Index(name = "idx_allocation_undo_requests_item", columnList = "order_item_id"),
        @Index(name = "idx_allocation_undo_requests_status", columnList = "status")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocationUndoRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "inventory_unit_id", nullable = false)
    private Long inventoryUnitId;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(nullable = false, length = 40)
    private UndoRequestStatus status;

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
}
