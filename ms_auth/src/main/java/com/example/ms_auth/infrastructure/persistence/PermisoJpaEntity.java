package com.example.ms_auth.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "permisos", uniqueConstraints = @UniqueConstraint(columnNames = {"recurso", "accion"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermisoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String recurso;

    @Column(nullable = false, length = 60)
    private String accion;

    private String descripcion;
}
