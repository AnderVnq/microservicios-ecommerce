# OMS de importación — microservicios con Spring Boot

Sistema de gestión de pedidos (OMS) para una empresa que vende en marketplaces (Falabella, Amazon, …) y compra los productos en EE. UU. para importarlos a Lima. Sigue cada **unidad** de un pedido desde la compra al proveedor hasta la entrega al cliente. Lleva el inventario físico con kardex valorizado y mantiene un catálogo maestro de productos con el que se enlazan los SKU de todo el sistema.

- **Java 17** · **Spring Boot 4.1** · Spring Cloud Gateway · Spring Data JPA
- **Keycloak** (OIDC / JWT) · **MySQL 8.4** (una base por servicio)
- Arquitectura **hexagonal** en cada servicio · Docker con imágenes en capas + JRE de `jlink` + distroless

---

## Arquitectura

![Arquitectura general](docs/diagrams/img/arquitectura.png)

| Servicio | Puerto | Base | Responsabilidad |
|---|---|---|---|
| `api-gateway` | 8080 | — | Punto de entrada único; enruta por path y valida el JWT |
| `ms-markets` | 8081 | `markets_db` | Markets donde se vende |
| `ms-clientes` | 8082 | `clientes_db` | Titulares y familiares (documento de importación) |
| `ms-pedidos` | 8083 | `pedidos_db` | Pedidos por unidad, cupo de importación, compras a proveedor, solicitudes de cambio de estado |
| `ms_auth` | 8084 | `usuarios_db` | Usuarios, roles y permisos (p. ej. `status-change/approve`) |
| `ms-inventory` | 8085 | `inventory_db` | Unidades físicas, asignación a pedidos, stock y kardex |
| `ms-products` | 8086 | `products_db` | Catálogo: productos, variaciones (SKU), categorías y equivalencias de SKU por market |
| Keycloak | 8180 | — | Emisión de tokens (realm `microservicios`) |

### Llamadas entre servicios

![Llamadas entre servicios](docs/diagrams/img/dependencias.png)

- `ms-pedidos` llama a `ms-inventory`, `ms_auth` y `ms-products` con un **token de servicio** (`client_credentials`, client `ms-pedidos`), y a `ms-clientes` reenviando el token del usuario.
- `ms-inventory` y `ms-products` reenvían el token del usuario a `ms-products` y `ms-markets`.
- Ninguna llamada remota se hace mientras se tiene un lock de base de datos.
- El gateway responde 403 a las rutas internas de inventario (`/api/v1/inventory/units/*/allocation` y `/api/v1/inventory/purchase-entries/**`). Solo `ms-pedidos` puede llamarlas, y `ms-inventory` lo verifica por el claim `azp` del token.

---

## Dominio

### Pedidos por unidad

Cada línea de un pedido se expande en **unidades**, una por cantidad pedida. Cada unidad tiene su propio estado, importador, historial e incidencias. El **cupo de importación** se cuenta por documento del titular y por año.

![Estados de una unidad de pedido](docs/diagrams/img/unidad-pedido.png)

Estados completos: `PROCESSING → PURCHASED → RECEIVED_AT_WAREHOUSE → READY_TO_SHIP → SHIPPED → ARRIVED_LIMA → (CUSTOMS_HOLD) → RECEIVED_LIMA → DISPATCHED → DELIVERED → CUSTOMER_RECEIVED`. Una unidad puede pasar a `CANCELLED` desde cualquier estado no terminal.

Retroceder un estado (deshacer una asignación, marcar unidades como no recibidas) crea una **solicitud de cambio de estado** que debe aprobar alguien con permiso `status-change/approve`. Quien tiene `status-change/apply-direct` (gerente o admin) aplica el cambio directo.

### Catálogo de productos y SKU

- Un **producto** tiene siempre un **SKU principal** y variaciones de color y/o talla. Cada variación es un SKU interno, único en todo el sistema.
- Cada market etiqueta el producto con su propio SKU. Ese SKU se guarda como **equivalencia** que apunta a un SKU interno.
- Peso y medidas se guardan **originales** (lb, oz, in, tal como vienen de Amazon) y **convertidos** (kg y cm). La conversión la hacen columnas calculadas de MySQL.
- Un scraper externo escribe el **precio de referencia** directo en la base. Triggers de MySQL guardan el historial de precios en UTC.
- Hay una **carga masiva** (`POST /api/v1/products/import`, hasta 5000 filas). Si el SKU ya existe, solo actualiza el precio y agrega equivalencias. Las diferencias en los demás campos se reportan, pero no se aplican.

![Crear pedido con resolución de SKU](docs/diagrams/img/pedido-sku.png)

Si el SKU del market no tiene equivalencia, o `ms-products` no responde, la unidad entra igual con `sku = null` y una incidencia `PRODUCT_LINK_PENDING`. Un job la enlaza cuando la equivalencia exista. Mientras tanto, esa unidad no se puede comprar ni asignar desde inventario.

### Compras a proveedor e inventario

![Compra a proveedor y llegada al almacén](docs/diagrams/img/compra-proveedor.png)

- Toda unidad comprada existe en `ms-inventory` desde que se registra la compra. Queda "en camino", reservada para su unidad de pedido o libre como sobrante. Si el pedido se cancela, la unidad física pasa a stock.
- La sincronización con inventario usa un **outbox** (`inventory_sync_tasks`) con reintentos. Registrar compras, llegadas y no recibidas exige la cabecera `Idempotency-Key`.
- Las llegadas se cuentan por tandas. Primero se llenan las unidades con fecha límite de envío más cercana.

Las versiones interactivas de los diagramas (zoom, búsqueda, tema claro u oscuro) están en [`docs/diagrams/*.html`](docs/diagrams/). Sus fuentes JSON están en [`docs/diagrams/src/`](docs/diagrams/src/).

---

## Cómo levantarlo

### Requisitos

- Docker Desktop
- MySQL 8.x corriendo en tu máquina (los servicios se conectan a `host.docker.internal`)
- JDK 17, solo para compilar o correr tests fuera de Docker

### 1. Base de datos (una sola vez)

```bash
cp docker/mysql/init.sql.example docker/mysql/init.sql   # ajusta la contraseña del usuario app
mysql -u root -p < docker/mysql/init.sql
# el esquema de ms-products incluye triggers: se aplica como root
mysql -u root -p products_db < ms-products/src/main/resources/db/schema.sql
```

En bases creadas antes de una funcionalidad, aplica los scripts de [`docker/mysql/migrations/`](docker/mysql/migrations/) en orden de fecha. Todos se pueden volver a ejecutar sin duplicar datos.

### 2. Variables y Keycloak

```bash
cp .env.example .env                                                  # credenciales de MySQL y secreto del client ms-pedidos
cp docker/keycloak/realm-export.json.example docker/keycloak/realm-export.json
```

### 3. Levantar todo

```bash
docker compose up -d --build
```

| URL | Qué es |
|---|---|
| http://localhost:8080 | API Gateway (punto de entrada) |
| http://localhost:8180 | Consola de Keycloak (`admin` / `admin` en desarrollo) |

Obtener un token de prueba:

```bash
curl -s -d grant_type=password -d client_id=api-gateway \
     -d username=testuser -d password=test123 \
     http://localhost:8180/realms/microservicios/protocol/openid-connect/token
```

### 4. Probar la API con Bruno

La colección [`bruno/`](bruno/) tiene una carpeta por recurso (Orders, Purchases, Products, Variants, Inventory, …), y cada petición viene documentada. Empieza por `Auth/Setup`, que crea los permisos de solicitudes de cambio de estado.

---

## Desarrollo

```bash
./mvnw -pl ms-pedidos test        # tests de un módulo
./mvnw test                       # todo el reactor
```

- `ms-products` usa **Testcontainers** (MySQL 8.4), así que Docker debe estar corriendo. Los demás servicios prueban contra H2.
- Cada `Dockerfile` usa como contexto la raíz del repo, porque necesita los `pom.xml` de todos los módulos. Si agregas un módulo, añade su `COPY <módulo>/pom.xml` en todos los Dockerfiles.
- Imágenes: el jar se separa en capas de Spring Boot. `jlink` arma un JRE mínimo con los módulos que detecta `jdeps`, y la imagen base es `gcr.io/distroless/base-debian12:nonroot`. El resultado pesa unos 100 MB comprimida y corre sin shell, como usuario no root.

### Estructura de cada servicio

```
src/main/java/com/example/<servicio>/
├── domain/          modelo y reglas de negocio (sin Spring)
├── application/
│   ├── port/in      casos de uso
│   ├── port/out     puertos hacia BD y otros servicios
│   └── service      implementación de los casos de uso
└── infrastructure/
    ├── persistence  entidades JPA y adaptadores
    ├── client       clientes REST hacia otros servicios
    ├── web          controllers, DTOs y manejo de errores
    └── config       seguridad y beans
```

Los diseños y planes de cada funcionalidad están en [`docs/superpowers/specs/`](docs/superpowers/specs/) y [`docs/superpowers/plans/`](docs/superpowers/plans/).
