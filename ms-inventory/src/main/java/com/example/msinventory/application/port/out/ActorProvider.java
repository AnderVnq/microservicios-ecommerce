package com.example.msinventory.application.port.out;

public interface ActorProvider {

    /** {@code sub} del JWT, si no {@code azp}, si no {@code "system"}. */
    String currentActor();
}
