package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.domain.model.SupplierStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "suppliers", uniqueConstraints =
        @UniqueConstraint(name = "uk_suppliers_name_normalized", columnNames = "name_normalized"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    /** Nombre en minusculas: emula un indice unico sin distinguir mayusculas. */
    @Column(name = "name_normalized", nullable = false, length = 150)
    private String nameNormalized;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(nullable = false, length = 40)
    private SupplierStatus status;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(length = 300)
    private String address;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.nameNormalized = normalize(name);
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.nameNormalized = normalize(name);
        this.updatedAt = Instant.now();
    }

    static String normalize(String name) {
        return name == null ? null : name.trim().toLowerCase(Locale.ROOT);
    }
}
