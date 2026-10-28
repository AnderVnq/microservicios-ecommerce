package com.example.ms_auth.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "aplicaciones_externas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AplicacionExternaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_client_id", nullable = false, unique = true, length = 100)
    private String keycloakClientId;

    @Column(nullable = false)
    private String nombre;

    private String empresa;

    @Column(nullable = false)
    private boolean activa;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "aplicacion_permisos",
            joinColumns = @JoinColumn(name = "aplicacion_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    @Builder.Default
    private Set<PermisoJpaEntity> permisos = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
