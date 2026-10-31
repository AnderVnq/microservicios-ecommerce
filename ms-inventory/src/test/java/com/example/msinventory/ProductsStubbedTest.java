package com.example.msinventory;

import com.example.msinventory.application.port.out.ProductsPort;
import org.junit.jupiter.api.BeforeEach;

import java.util.Optional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Base de los tests con contexto completo: ms-products simulado, todo SKU existe por defecto con esa escritura. */
public abstract class ProductsStubbedTest {

    @MockitoBean protected ProductsPort products;

    @BeforeEach
    void stubProductsExists() {
        when(products.canonicalSku(anyString())).thenAnswer(inv -> Optional.of(inv.<String>getArgument(0)));
    }
}
