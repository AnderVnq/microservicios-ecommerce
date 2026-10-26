package com.example.msmarkets.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Modelo de dominio puro: sin anotaciones de persistencia ni de framework web.
 * El mapeo a tabla vive en infrastructure.persistence.MarketJpaEntity.
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class Market {

    private Long id;
    private String code;
    private String name;
    private String currency;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static Market createNew(String code, String name, String currency) {
        return Market.builder()
                .code(code)
                .name(name)
                .currency(currency)
                .active(true)
                .build();
    }

    public void deactivate() {
        this.active = false;
    }

    public void rename(String code, String name, String currency) {
        this.code = code;
        this.name = name;
        this.currency = currency;
    }
}
