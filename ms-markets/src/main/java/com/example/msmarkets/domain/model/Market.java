package com.example.msmarkets.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Modelo de dominio puro: sin anotaciones de persistencia ni de framework web.
 * El mapeo a tabla vive en infrastructure.persistence.MarketJpaEntity.
 *
 * Un Market es una variante/canal de venta dentro de una misma marca
 * (ej: marca="Falabella", tipo="Express"/"USA"/"24h"/"China"). La marca se
 * repite entre varios Market; lo que cambia por tipo es el SLA de entrega.
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class Market {

    private Long id;
    private String marca;
    private String tipo;
    private Integer slaEntregaHoras;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static Market createNew(String marca, String tipo, Integer slaEntregaHoras) {
        return Market.builder()
                .marca(marca)
                .tipo(tipo)
                .slaEntregaHoras(slaEntregaHoras)
                .active(true)
                .build();
    }

    public void deactivate() {
        this.active = false;
    }

    public void actualizar(String marca, String tipo, Integer slaEntregaHoras) {
        this.marca = marca;
        this.tipo = tipo;
        this.slaEntregaHoras = slaEntregaHoras;
    }
}
