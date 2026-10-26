package com.example.msmarkets.infrastructure.web;

import com.example.msmarkets.application.port.in.MarketUseCase;
import com.example.msmarkets.infrastructure.web.dto.MarketRequest;
import com.example.msmarkets.infrastructure.web.dto.MarketResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Adaptador driver: traduce HTTP al puerto de entrada MarketUseCase. */
@RestController
@RequestMapping("/api/v1/markets")
@RequiredArgsConstructor
public class MarketController {

    private final MarketUseCase marketUseCase;

    @GetMapping
    public List<MarketResponse> findAll() {
        return marketUseCase.findAll().stream().map(MarketResponse::from).toList();
    }

    @GetMapping("/{id}")
    public MarketResponse findById(@PathVariable Long id) {
        return MarketResponse.from(marketUseCase.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MarketResponse create(@Valid @RequestBody MarketRequest request) {
        return MarketResponse.from(marketUseCase.create(request.code(), request.name(), request.currency()));
    }

    @PutMapping("/{id}")
    public MarketResponse update(@PathVariable Long id, @Valid @RequestBody MarketRequest request) {
        return MarketResponse.from(marketUseCase.update(id, request.code(), request.name(), request.currency()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        marketUseCase.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
