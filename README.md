# Proyecto de Microservicios con Spring Cloud + Angular

Aplicación distribuida de gestión con:

- **Frontend**: Angular 22 (RC en `localhost:4200`)
- **Backend**: Spring Boot 4 + Spring Cloud 2025.1 (Java 17), organizado en microservicios

## Arquitectura

```
                    +----------------------------------------------+
Browser (Angular) ->|  API Gateway  (puerta de enlace)  :8080      |
                    +----------------------------------------------+
                        |                    |                    |
                 lb://alumnos         lb://administracion   (descubrimiento)
                        |                    |
              +------------------+  +---------------------+
              |  alumnos :8101   |  | administracion :8102 |
              |  H2 ./data/alumnos|  | H2 ./data/administracion |
              +--------+---------+  +----------+----------+
                       |                        |
                       +---- RabbitMQ (5672) ---+
                             (mensajería)

  Registry (Eureka) :8761  <- todos los servicios se registran aqui
  Config Server     :8888  <- centraliza la configuracion de cada microservicio
```

### Componentes del backend

| Módulo              | Carpeta                 | Puerto | Descripción |
|---------------------|-------------------------|--------|-------------|
| Registry (Eureka)   | `backend/registry`      | 8761   | Descubrimiento de servicios. Todos se registran aquí y el gateway los localiza |
| Config Server       | `backend/config-server` | 8888   | Centraliza la configuración (`alumnos.yml`, `administracion.yml`, `gateway.yml`) |
| API Gateway         | `backend/gateway`       | 8080   | Puerta de enlace con rutas `/api/alumnos/**` y `/api/administracion/**`, CORS y balanceo `lb://`. Emite y valida los JWT (`/auth/login`, `/auth/refresh`, `/auth/me`) |
| microservicio alumnos | `backend/alumnos`     | 8101   | CRUD de alumnos (tabla `ALUMNOS` en H2). Publica eventos `alumno.creado`. Resource server JWT: valida el token por su cuenta |
| microservicio administracion | `backend/administracion` | 8102 | CRUD de docentes (tabla `DOCENTES` en H2). Publica eventos `docente.creado`. Resource server JWT: valida el token por su cuenta |
| Spring Boot Admin   | `backend/admin-server`  | 9090   | Panel de monitoreo: estado, salud, métricas y logs de todos los microservicios (descubiertos vía Eureka). Login `admin` / `admin123` |
| RabbitMQ            | `docker-compose.yml`    | 5672 / 15672 | Broker de mensajería. Los microservicios publican y consumen eventos entre sí |

### Mensajería (RabbitMQ)

- Exchange `microservicios.exchange` (topic).
- **alumnos** publica `alumno.creado`; **administracion** consume ese evento (cola `microservicios.cola.administracion`, binding `alumno.*`).
- **administracion** publica `docente.creado`; **alumnos** consume ese evento (cola `microservicios.cola.alumnos`, binding `docente.*`).
- Los eventos se ven en la consola de cada servicio (`ADMINISTRACION | Evento recibido desde RabbitMQ: ...`).

## Requisitos

- Java 17 (JDK)
- Node.js 18.19+ (npm)
- Docker (para RabbitMQ)
- Maven se descarga solo con el wrapper `mvnw`

## Puesta en marcha

### 1) RabbitMQ

```powershell
docker compose up -d
```

Consola de gestión: http://localhost:15672 (`guest` / `guest`)

### 2) Compilar el backend (primera vez)

```powershell
backend\registry\mvnw.cmd package -DskipTests
backend\config-server\mvnw.cmd package -DskipTests
backend\alumnos\mvnw.cmd package -DskipTests
backend\administracion\mvnw.cmd package -DskipTests
backend\gateway\mvnw.cmd package -DskipTests
```

### 3) Levantar los microservicios

**Opción A – Script automático** (recomendado: usa las bases H2 de la carpeta `data\`):

```powershell
.iniciar-backend.ps1           # levanta registry, config-server, alumnos, administracion, gateway y admin-server
.\detener-backend.ps1           # los detiene (y opcionalmente RabbitMQ)
```

**Opción B – Individual desde cada IDE** (Eclipse/IntelliJ/VS Code): ejecutar la clase `*Application` de cada proyecto. Orden sugerido: `registry` → `config-server` → `alumnos` + `administracion` → `gateway`.

### 4) Frontend Angular

```powershell
cd frontend
npm install
npm start        # http://localhost:4200
```

El frontend consume siempre el **API Gateway** (`http://localhost:8080`), nunca los microservicios directamente.

## Seguridad: Spring Security + JWT

La protección es **por capas**: el gateway filtra, y cada microservicio se vuelve a validar por su cuenta. Así, aunque alguien saltee el gateway y llame directo al puerto `8101`/`8102`, la API sigue protegida.

```
POST /auth/login ──> gateway (WebFlux, resource server) ──> alumnos / administracion
                          │                                        │
                    emite y valida                        también validan firma,
                    access + refresh                      issuer, expiry y roles
```

### Endpoints de autenticación (solo en el gateway)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/auth/login` | Devuelve `accessToken` (15 min), `refreshToken` (8 h), `expiresIn`, `username` y `roles` |
| POST | `/auth/refresh` | Renueva el access token a partir de un refresh token válido |
| GET | `/auth/me` | Usuario y roles del token presentado |

### Usuarios de demo

Definidos en el **Config Server**, con contraseñas hasheadas en BCrypt (`config/application.yml` y `config/gateway.yml`):

| Usuario | Contraseña | Rol | Puede iniciar sesión |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | Sí |
| `user` | `user123` | USER | Sí |
| `monitor` | `monitor123` | ADMIN (monitoring) | **No**: solo autenticación HTTP Basic contra `/actuator/**` |

`monitor` vive en un `AuthenticationManager` separado del login, de modo que esas credenciales no sirven para obtener un token de usuario.

### Matriz de autorización

| Recurso | Sin token | `USER` | `ADMIN` |
|---|---|---|---|
| `GET /api/**` | 401 | 200 | 200 |
| `POST` / `PUT` / `DELETE /api/**` | 401 | **403** | 200 |
| `/auth/me` | 401 | 200 | 200 |
| `/actuator/health`, `/actuator/info` | 200 | 200 | 200 |
| Resto de `/actuator/**` | 401 (Basic `monitor`) | — | — (Basic `monitor`) |

Cada escritura está protegida dos veces: por las reglas de URL del `SecurityConfig` y por `@PreAuthorize("hasRole('ADMIN')")` en el controlador.

### Detalles de los tokens

- Firmados con **HS256**; el claim `roles` (`["ADMIN"]`) se convierte en la autoridad `ROLE_ADMIN`.
- Access y refresh usan **secretos distintos** y un claim `typ` distinto, así que un access token no sirve como refresh ni al revés.
- El microservicio valida `iss`, `exp` y la firma: no confía en que el gateway ya lo hizo.

### Uso desde consola

```powershell
# 1) Obtener tokens
$login = Invoke-RestMethod -Uri http://localhost:8080/auth/login -Method Post `
  -ContentType 'application/json' -Body '{"username":"admin","password":"admin123"}'
$token = $login.accessToken

# 2) Usar el access token
Invoke-RestMethod -Uri http://localhost:8080/api/alumnos -Headers @{ Authorization = "Bearer $token" }

# 3) Renovar el access token cuando caduca
$login2 = Invoke-RestMethod -Uri http://localhost:8080/auth/refresh -Method Post `
  -ContentType 'application/json' -Body (@{ refreshToken = $login.refreshToken } | ConvertTo-Json)
```

### Frontend Angular

- Vista **Iniciar sesión** en `http://localhost:4200`; guarda los tokens en `localStorage` junto con los roles.
- Un **interceptor** renueva el access token automáticamente antes de que caduque, y reintenta una vez tras un `401`; si el refresh también falla, cierra la sesión.
- Un **guard** de rol protege la vista `/seguridad` (solo ADMIN), donde se ven los claims del token, su expiración y la opción de renovar.
- Con sesión `USER` los listados se ven, pero los formularios y botones de alta/edición/borrado no aparecen.

### Spring Boot Admin

El panel (`http://localhost:9090`) pide login HTTP Basic: **`admin` / `admin123`**. Para leer los endpoints de actuator de cada servicio usa internamente la cuenta `monitor` (`spring.boot.admin.instance-auth`).

### Limitaciones conocidas

- `registry` y `config-server` no tienen Spring Security: solo exponen `/actuator/health` e `/actuator/info`. Si exposing endpoints con datos sensibles, habría que protegerlos.
- El refresh token no se invalida al usarlo (no hay rotación ni lista negra) y el secreto JWT es simétrico y está en el Config Server: en producción conviene un proveedor de claves externo o JWT asimétrico (RS256) con rotación de llaves.
- Con Spring Boot 4, el cliente Eureka registra el puerto como objeto (`port: {"$":8080,"@enabled":"true"}`) en los cinco servicios. Es una incompatibilidad previa del proyecto, ajena a la seguridad, y hace que Spring Boot Admin no achieve a leer el actuator de las instancias aunque estén registradas.

## Prueba rápida

> Requiere el paso previo de **Seguridad** (obtener el `$token` con el comando anterior).

```powershell
# Listar alumnos a través del gateway (cualquier usuario autenticado)
Invoke-RestMethod -Uri http://localhost:8080/api/alumnos -Headers @{ Authorization = "Bearer $token" }

# Crear un alumno (solo ADMIN; dispara el evento alumno.creado en RabbitMQ)
Invoke-RestMethod -Uri http://localhost:8080/api/alumnos -Method Post -Headers @{ Authorization = "Bearer $token" } `
  -ContentType 'application/json' -Body '{"nombre":"Lucia","apellido":"Fernandez","curso":"Quimica"}'

# Crear un docente (solo ADMIN; dispara el evento docente.creado en RabbitMQ)
Invoke-RestMethod -Uri http://localhost:8080/api/administracion -Method Post -Headers @{ Authorization = "Bearer $token" } `
  -ContentType 'application/json' -Body '{"nombre":"Juan","apellido":"Paredes","especialidad":"Historia"}'
```

Con un token de `user`, los dos `POST` anteriores responden `403 Forbidden`.

Luego revisar la consola de `administracion` (recibió `alumno.creado`) y de `alumnos` (recibió `docente.creado`).

## Configuración centralizada

La configuración real de cada microservicio vive en `backend/config-server/src/main/resources/config/`:

- `application.yml` → valores compartidos (RabbitMQ, Eureka) y la seguridad: secreto del access token, secreto y expiración del refresh token, issuer y cuenta de monitoreo (`app.jwt`, `app.security.monitoring`)
- `alumnos.yml`, `administracion.yml`, `gateway.yml` → puertos, datasource, rutas del gateway y los usuarios de la API con su hash BCrypt (`app.security.users`)

Cada servicio la consume al arrancar vía `spring.config.import: optional:configserver:http://localhost:8888`.

> Los `application.yml` de cada microservicio llevan valores de respaldo para poder arrancar sin el Config Server, pero **si el Config Server está levantado, sus valores ganan**: por eso hay que recompilar y reiniciar el Config Server (`backend\config-server\mvnw.cmd package -DskipTests`) después de tocar su configuración.

## Puertos

| Servicio | URL |
|---|---|
| Frontend Angular | http://localhost:4200 |
| API Gateway | http://localhost:8080 |
| Registry (Eureka) | http://localhost:8761 |
| Config Server | http://localhost:8888 |
| Alumnos (directo) | http://localhost:8101/api/alumnos |
| Administración (directo) | http://localhost:8102/api/administracion |
| **Spring Boot Admin** | http://localhost:9090 |
| RabbitMQ (UI) | http://localhost:15672 |