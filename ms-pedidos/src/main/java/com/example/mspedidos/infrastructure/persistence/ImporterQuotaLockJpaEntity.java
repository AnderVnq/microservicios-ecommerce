package com.example.mspedidos.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "importer_quota_locks", uniqueConstraints =
        @UniqueConstraint(name = "uk_importer_quota_locks_doc_year",
                columnNames = {"document_type", "document_number", "quota_year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImporterQuotaLockJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType;

    @Column(name = "document_number", nullable = false, length = 40)
    private String documentNumber;

    @Column(name = "quota_year", nullable = false)
    private int quotaYear;
}
