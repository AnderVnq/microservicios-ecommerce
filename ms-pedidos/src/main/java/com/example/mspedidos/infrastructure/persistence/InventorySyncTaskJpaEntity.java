package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.domain.model.InventorySyncTaskStatus;
import com.example.mspedidos.domain.model.InventorySyncTaskType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;

import java.time.Instant;

@Entity
@Table(name = "inventory_sync_tasks",
        indexes = @Index(name = "idx_inventory_sync_tasks_status_purchase", columnList = "status, purchase_id, id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventorySyncTaskJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(nullable = false, length = 40)
    private InventorySyncTaskType type;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(nullable = false, length = 40)
    private InventorySyncTaskStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "done_at")
    private Instant doneAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
