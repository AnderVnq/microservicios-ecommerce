package com.example.mspedidos.application.port.out;

/** Estado derivado de sincronizacion con inventario: alguna tarea FAILED, si no alguna PENDING, si no OK. */
public enum InventorySyncState { OK, PENDING, FAILED }
