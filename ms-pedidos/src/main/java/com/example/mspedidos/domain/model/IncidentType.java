package com.example.mspedidos.domain.model;

public enum IncidentType {
    IMPORTER_ASSIGNMENT_PENDING, PRODUCT_LINK_PENDING, DELAY, VARIANT_CHANGE, OTHER;

    /** Las incidencias gestionadas por el sistema no las abre ni cierra el operador. */
    public boolean isSystemManaged() {
        return this == IMPORTER_ASSIGNMENT_PENDING || this == PRODUCT_LINK_PENDING;
    }
}
