package com.example.msinventory.infrastructure.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Siembra el catalogo de condiciones; idempotente (solo inserta los codigos faltantes). */
@Component
class ConditionSeeder implements ApplicationRunner {

    private static final Map<String, String> SEED = new LinkedHashMap<>();

    static {
        SEED.put("NEW", "Nuevo");
        SEED.put("OPEN_BOX", "Open box");
        SEED.put("REFURBISHED", "Restaurado");
        SEED.put("USED", "Usado");
    }

    private final SpringDataItemConditionRepository repository;

    ConditionSeeder(SpringDataItemConditionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        SEED.forEach((code, name) -> {
            if (!repository.existsById(code)) {
                repository.save(new ItemConditionJpaEntity(code, name, true));
            }
        });
    }
}
