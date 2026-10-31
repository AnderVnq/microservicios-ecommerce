package com.example.msproducts.application.port.in;

/** Dato estable que llego distinto al guardado y no se aplico ("diferencia no aplicada"). */
public record IgnoredDifference(String field, String current, String received) {
}
