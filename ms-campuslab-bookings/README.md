# ms-campuslab-bookings

Microservicio Spring Boot para el CRUD de reservas de laboratorios/equipos, con
máquina de estados y persistencia en Oracle.

## Stack
- Spring Boot 3.3.4 / Java 17
- Spring Data JPA + Hibernate (dialecto Oracle)
- Oracle JDBC (`ojdbc11`)
- Flyway (migraciones / DDL versionado)
- Spring Security (OAuth2 Resource Server, JWT)
- Lombok

## Máquina de estados

```
SOLICITADA ──► APROBADA ──► EN_PREPARACION ──► EN_USO ──► DEVUELTA
    │              │               │
    └──────────────┴───────────────┴──────► CANCELADA
```

**Regla crítica de negocio** (`ReservaServiceImpl.validarReglasDeNegocioPorEstadoDestino`):
no se puede mover una reserva a `EN_USO` si no fue `APROBADA` previamente por un
usuario con rol `TECNICO` o `ADMIN`. Esto se refuerza en dos capas:

1. **Servicio**: solo permite pasar a `APROBADA` si el usuario autenticado tiene
   rol `TECNICO` o `ADMIN` (`ROLES_QUE_PUEDEN_APROBAR`), y registra `aprobadoPor`
   + `fechaAprobacion`. Al pedir `EN_USO`, exige que esos dos campos existan
   (`reserva.tieneAprobacionRegistrada()`); si no, lanza
   `TransicionEstadoInvalidaException` (HTTP 409).
2. **Base de datos**: constraint `CK_RESERVAS_EN_USO_REQ_APROB` en el DDL
   (`V1__create_reservas_table.sql`) bloquea a nivel de fila cualquier `UPDATE`
   que deje `ESTADO='EN_USO'` sin `APROBADO_POR`/`FECHA_APROBACION`, como defensa
   en profundidad ante escrituras directas a la BD.

## Endpoints

| Método | Path                        | Descripción                              | Auth |
|--------|-----------------------------|-------------------------------------------|------|
| POST   | `/api/bookings`             | Crear reserva (nace en `SOLICITADA`)      | autenticado |
| GET    | `/api/bookings/{id}`        | Detalle de una reserva                    | autenticado |
| GET    | `/api/bookings`             | Listado con filtros + paginación          | autenticado |
| PUT    | `/api/bookings/{id}/status` | Cambiar estado                            | `TECNICO`, `ADMIN`, `ESTUDIANTE`, `DOCENTE` (rol fino se valida en servicio) |

### Ejemplo: crear reserva
```http
POST /api/bookings
Content-Type: application/json

{
  "recursoId": 10,
  "usuarioSolicitanteId": 20,
  "fechaInicio": "2026-09-20T09:00:00",
  "fechaFin": "2026-09-20T11:00:00",
  "observaciones": "Práctica de circuitos"
}
```

### Ejemplo: listar con filtros
```
GET /api/bookings?estado=SOLICITADA&recursoId=10&page=0&size=20&sort=fechaInicio,asc
```

### Ejemplo: aprobar (requiere JWT con rol TECNICO o ADMIN)
```http
PUT /api/bookings/5/status
Authorization: Bearer <jwt>
Content-Type: application/json

{ "nuevoEstado": "APROBADA" }
```

### Ejemplo: intento inválido (bloqueado por la regla crítica)
```http
PUT /api/bookings/5/status
Authorization: Bearer <jwt>
Content-Type: application/json

{ "nuevoEstado": "EN_USO" }
```
Si la reserva nunca pasó por `APROBADA` (o está en `SOLICITADA`), responde:
```json
HTTP 409 Conflict
{
  "status": 409,
  "error": "Conflict",
  "mensaje": "No se puede pasar la reserva a EN_USO: no ha sido APROBADA previamente por un tecnico o admin"
}
```

## Configuración (variables de entorno)

| Variable          | Descripción                                   | Default                  |
|-------------------|------------------------------------------------|---------------------------|
| `DB_HOST`         | Host de Oracle                                 | `localhost`               |
| `DB_PORT`         | Puerto de Oracle                               | `1521`                    |
| `DB_SERVICE`      | Nombre del servicio/PDB                        | `XEPDB1`                  |
| `DB_USERNAME`     | Usuario de BD                                  | `campuslab_bookings`      |
| `DB_PASSWORD`     | Password de BD                                 | `changeme`                |
| `AUTH_ISSUER_URI` | Issuer URI del IdP (Keycloak / ms-auth) que firma los JWT | `http://localhost:8080/realms/campuslab` |

> **Nota sobre roles en el JWT**: `SecurityConfig` espera un claim `roles` (array
> de strings, ej. `["ADMIN"]`) en el token. Si tu IdP real (ej. Keycloak) los
> entrega anidados en `realm_access.roles`, ajusta `jwtAuthenticationConverter()`
> para leer ese claim en su lugar, o configura un *claim mapper* en el IdP para
> aplanarlo a `roles` en el token.

## Ejecutar

```bash
mvn clean package
mvn spring-boot:run
```

Flyway crea automáticamente la tabla `RESERVAS` (y su secuencia) contra la BD
Oracle configurada, al arrancar la aplicación.

## Tests

```bash
mvn test
```

Incluye `ReservaServiceImplTest`, que cubre específicamente la regla crítica
(bloqueo de `EN_USO` sin aprobación previa, bloqueo de `APROBADA` sin rol
adecuado, y transiciones fuera de la topología).
