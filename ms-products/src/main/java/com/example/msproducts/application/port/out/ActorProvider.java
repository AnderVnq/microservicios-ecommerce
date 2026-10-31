package com.example.msproducts.application.port.out;

/**
 * Puerto de salida que entrega el usuario que ejecuta la operacion actual,
 * usado para los campos de auditoria (created_by / updated_by).
 */
public interface ActorProvider {

    /** Identificador del usuario autenticado en la operacion actual. */
    String currentActor();
}
