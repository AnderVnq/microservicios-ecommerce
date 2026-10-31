package com.example.msproducts.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/** Solo lectura: lo escriben los triggers de la base. */
@Entity
@Immutable
@Table(name = "variant_price_history")
@Getter
@NoArgsConstructor
public class PriceHistoryJpaEntity {

    @Id
    private Long id;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3)
    private String currency;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
