package com.example.msinventory.infrastructure.web;

import com.example.msinventory.application.port.in.ItemConditionUseCase;
import com.example.msinventory.infrastructure.web.dto.ConditionResponse;
import com.example.msinventory.infrastructure.web.dto.CreateConditionRequest;
import com.example.msinventory.infrastructure.web.dto.UpdateConditionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adaptador driver: catalogo de condiciones. */
@RestController
@RequestMapping("/api/v1/inventory/conditions")
@RequiredArgsConstructor
public class ItemConditionController {

    private final ItemConditionUseCase conditions;

    @GetMapping
    public List<ConditionResponse> findAll() {
        return conditions.findAll().stream().map(ConditionResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConditionResponse create(@Valid @RequestBody CreateConditionRequest request) {
        return ConditionResponse.from(conditions.create(request.code(), request.name()));
    }

    @PatchMapping("/{code}")
    public ConditionResponse update(@PathVariable String code, @Valid @RequestBody UpdateConditionRequest request) {
        return ConditionResponse.from(conditions.update(code, request.name(), request.active()));
    }
}
