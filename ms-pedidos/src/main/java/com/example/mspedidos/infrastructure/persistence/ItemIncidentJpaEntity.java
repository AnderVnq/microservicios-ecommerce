package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.domain.model.IncidentScope;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "item_incidents", indexes =
        @Index(name = "idx_item_incidents_item", columnList = "order_item_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemIncidentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(nullable = false, length = 40)
    private IncidentType type;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(nullable = false, length = 40)
    private IncidentScope scope;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(nullable = false, length = 40)
    private IncidentStatus status;

    @Column(name = "opened_by", nullable = false)
    private String openedBy;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
