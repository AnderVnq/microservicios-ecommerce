package com.example.msproducts;

import com.example.msproducts.application.port.out.ActorProvider;
import com.example.msproducts.application.port.out.MarketsPort;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Base de pruebas con MySQL real (Testcontainers): el esquema usa columnas
 * generadas y triggers que H2 no soporta. Se conecta como root porque los
 * triggers lo exigen con el binlog activo.
 */
@SpringBootTest
public abstract class MySqlTestSupport {

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withUsername("root")
            .withPassword("test")
            // Zona del servidor distinta de UTC: asi se detecta si NOW() de los triggers y Java no coinciden
            .withEnv("TZ", "America/Lima")
            .withInitScript("db/schema.sql");

    static {
        MYSQL.start();
    }

    @MockitoBean
    protected MarketsPort marketsPort;

    @MockitoBean
    protected ActorProvider actorProvider;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void limpiarTablas() {
        Mockito.when(actorProvider.currentActor()).thenReturn("op-1");
        // Hijos primero por las claves foraneas
        jdbc.update("DELETE FROM variant_price_history");
        jdbc.update("DELETE FROM variant_market_skus");
        jdbc.update("DELETE FROM product_variants");
        jdbc.update("DELETE FROM products");
        // Arbol de hasta 3 niveles: los mas profundos primero
        jdbc.update("DELETE FROM categories WHERE level = 3");
        jdbc.update("DELETE FROM categories WHERE level = 2");
        jdbc.update("DELETE FROM categories");
    }
}
