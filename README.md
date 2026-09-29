# Colegio de microservicios · Spring Boot 4 + Spring Cloud

Gestión de un colegio repartida en microservicios, con autenticación propia,
configuración centralizada, descubrimiento de servicios, mensajería y
monitorización.

**No hay frontend.** La única interfaz visual es la **documentación interactiva
de cada API (Swagger UI)**, tematizada con los colores del proyecto. Es la forma
de consultar y probar la API desde el navegador, con el botón *Try it out*.

## Arrancar todo con un comando

```bash
docker compose up --build
```

Cuando los contenedores estén levantados:

| Qué | Dónde | Credenciales |
|---|---|---|
| **API Gateway** (entrada única) | http://localhost:8080 | — |
| Documentación de autenticación | http://localhost:8080/auth/docs/index.html | — |
| Documentación de alumnos | http://localhost:8080/api/alumnos/docs/index.html | — |
| Documentación de administración | http://localhost:8080/api/administracion/docs/index.html | — |
| Registro (Eureka) | http://localhost:8761 | — |
| Config Server | http://localhost:8888 | — |
| Spring Boot Admin | http://localhost:9090 | `admin` / `admin123` |
| RabbitMQ (consola) | http://localhost:15672 | `guest` / `guest` |

> Toda la documentación se alcanza **a través del gateway**, sin iniciar sesión:
> el gateway es la única puerta de entrada, igual que en producción.
>
> Cada servicio expone además su Swagger *estándar* (sin el tema del proyecto)
> en `<prefijo>/swagger-ui.html` y su JSON en `<prefijo>/v3/api-docs`. La página
> de `docs/index.html` es la que lleva el tema: reutiliza los mismos assets y
> apunta al mismo JSON, pero carga el CSS del proyecto.

Detener todo:

```bash
docker compose down          # conserva los datos
docker compose down -v       # borra también bases de datos y cola
```

## Arquitectura

```
                      +------------------------------------------+
   Navegador  ----->  |  API Gateway  :8080   (WebFlux, reactivo) |
                      |  enruta + autoriza, no emite tokens      |
                      +------------------------------------------+
                        |            |             |            |
                 /auth/**      /api/alumnos  /api/administracion  /actuator/**
                        |            |             |            |
              +---------v--+  +------v------+  +---v---------+  |
              | auth  :8100|  | alumnos    |  | administracion|  |
              | login,     |  | :8101      |  | :8102        |  |
              | refresh,   |  | CRUD       |  | CRUD         |  |
              | logout, me |  | alumnos    |  | docentes     |  |
              +------------+  +------+------+  +------+-------+  |
                                 |                    |          |
                                 +---- RabbitMQ ------+   Spring |
                                      (5672)           Boot Admin
                                                            (9090)

   Registry / Eureka :8761  <- descubrimiento de servicios
   Config Server     :8888  <- secretos, rutas y usuarios compartidos
```

### Módulos

| Módulo | Ruta | Puerto | Función |
|---|---|---|---|
| Registry | `backend/registry` | 8761 | Descubrimiento de servicios (Eureka) |
| Config Server | `backend/config-server` | 8888 | Fuente única de configuración compartida |
| Servicio de autenticación | `backend/auth` | 8100 | **Emite y renueva los tokens.** Único punto que comprueba credenciales |
| Microservicio de alumnos | `backend/alumnos` | 8101 | CRUD de alumnos (H2). Publica `alumno.creado` |
| Microservicio de administración | `backend/administracion` | 8102 | CRUD de docentes (H2). Publica `docente.creado` |
| API Gateway | `backend/gateway` | 8080 | Ruta `/auth/**`, `/api/**` y `/actuator/**`; valida el token y aplica los roles |
| Spring Boot Admin | `backend/admin-server` | 9090 | Panel de estado, métricas y logs de los servicios |
| RabbitMQ | `docker-compose.yml` | 5672 / 15672 | Bus de eventos entre microservicios |

### Mensajería

- Exchange `microservicios.exchange` (tipo *topic*).
- **alumnos** publica `alumno.creado`; **administración** lo consume
  (cola `microservicios.cola.administracion`, binding `alumno.*`).
- **administración** publica `docente.creado`; **alumnos** lo consume
  (cola `microservicios.cola.alumnos`, binding `docente.*`).
- Cada evento recibido aparece en el log del servicio que lo consume.

## Seguridad: cookies `HttpOnly` en lugar de tokens en el navegador

El único cambio de fondo respecto a un backend clásico está en **cómo se
entrega la sesión**. No hay frontend, así que no hay `localStorage` donde
guardar un token: los tokens viajan en **cookies `HttpOnly`**, que el navegador
envía solo y que ningún script puede leer.

```
POST /auth/login
   │
   ├─► Set-Cookie: access_token=…   HttpOnly  Path=/       15 min
   └─► Set-Cookie: refresh_token=…  HttpOnly  Path=/auth    8 h
                                  (cuerpo: sin tokens)
```

Después, cualquier petición a `/api/...` incluye la cookie automáticamente y el
gateway la convierte en `Authorization: Bearer` antes de validar. Para `curl` o
Postman, el token también se acepta en la cabecera.

Detalles que importan:

- **El login no devuelve tokens en el cuerpo.** Solo la cookie, que es
  inaccesible desde JavaScript.
- **`Path` restringido en el refresh token.** Solo viaja a `/auth/refresh`, nunca
  a las peticiones de datos.
- **`SameSite=Lax`** mantiene la protección frente a CSRF sin romper el flujo.
- **`secure: false`** porque en local no hay HTTPS. Con TLS detrás de un proxy
  hay que ponerlo en `true` en `config-server/.../config/auth.yml`.
- **Rotación real de refresh token.** Cada renovación emite un token nuevo con un
  identificador distinto (`jti`) y anula el anterior. Si alguien reutiliza un
  refresh token ya canjeado, el servicio detecta el *replay* y cierra la sesión.
- La cuenta de monitorización (`monitor`) vive en un `AuthenticationManager`
  separado: **no puede iniciar sesión** ni conseguir un token de usuario.

### Endpoints de autenticación

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/auth/login` | Comprueba credenciales y deja las cookies de sesión |
| POST | `/auth/refresh` | Renueva el access token; rota y anula el refresh token |
| POST | `/auth/logout` | Revoca la sesión y borra las cookies |
| GET | `/auth/me` | Usuario y roles del token presentado |

### Usuarios de demo

Definidos en el Config Server, con contraseñas hasheadas en BCrypt
(`backend/config-server/src/main/resources/config/auth.yml`):

| Usuario | Contraseña | Rol | ¿Puede iniciar sesión? |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | Sí |
| `user` | `user123` | USER | Sí |
| `monitor` | `monitor123` | ADMIN (monitoring) | **No**: solo HTTP Basic contra `/actuator/**` |

### Matriz de autorización

| Recurso | Sin token | `USER` | `ADMIN` |
|---|---|---|---|
| `GET /api/**` | 401 | 200 | 200 |
| `POST` / `PUT` / `DELETE /api/**` | 401 | **403** | 200 |
| `GET /auth/me` | 401 | 200 | 200 |
| `/actuator/health`, `/actuator/info` | 200 | 200 | 200 |
| Resto de `/actuator/**` | 401 (Basic `monitor`) | — | — (Basic `monitor`) |

### La protección es por capas

```
POST /auth/login ─► gateway ─► auth: comprueba usuario y contraseña
                            └──► emite cookies

GET /api/alumnos ─► gateway: valida firma, emisor, caducidad y rol
               └──► alumnos: vuelve a validar el token por su cuenta
```

Cada microservicio **vuelve a validar el token** aunque ya haya pasado el
gateway, con el mismo secreto. Así, si alguien salta el gateway y llama
directamente al puerto `8101` o `8102`, la API sigue protegida.

Y cada escritura está protegida dos veces: por las reglas de URL del
`SecurityConfig` y por `@PreAuthorize("hasRole('ADMIN')")` en el controlador.

### Qué se valida en cada token

| Comprobación | Dónde | Por qué |
|---|---|---|
| Firma HS256 | gateway y cada servicio | Solo quien tiene el secreto puede emitirlo |
| Emisor (`iss`) | todos | Evita aceptar tokens de otra aplicación |
| Caducidad (`exp`) | todos | Un token de 15 min no vale para siempre |
| Tipo (`typ=access`) | todos | Un refresh token nunca sirve para llamar a la API |

Access y refresh usan **secretos distintos**, así que un tipo de token no puede
suplantar al otro ni aunque compartieran firma.

## Configuración centralizada

La configuración compartida vive en
`backend/config-server/src/main/resources/config/`:

| Archivo | Contenido |
|---|---|
| `application.yml` | Secretos JWT, emisor, duraciones, nombre de la cookie, cuenta de monitorización, Eureka y actuator |
| `auth.yml` | Usuarios que pueden iniciar sesión y configuración de las cookies |
| `gateway.yml` | Rutas del gateway (`lb://auth`, `lb://alumnos`, `lb://administracion`) |

Los servicios la consumen al arrancar con
`spring.config.import: optional:configserver:http://localhost:8888` (o
`http://configserver:8888` con el perfil `docker`).

**El reparto es deliberado y no se solapa:**

- En el **Config Server** vive todo lo que *debe* ser idéntico en todos los
  servicios y es peligroso de duplicar: los secretos, las rutas, los usuarios.
- En el **`application.yml` de cada servicio** vive lo que es *propio* de él:
  su puerto, su nombre, su base de datos y su cola.

> Si un servicio declarara también el secreto JWT en su `application.yml`, su
> copia local **tendría precedencia** sobre la del Config Server, y dos
> servicios podrían acabar validando tokens con claves distintas. Por eso no hay
> valores de respaldo: o están en el Config Server, o no están.

Para cambiar una ruta o un secreto hay que reconstruir y reiniciar el Config
Server: `docker compose up -d --build configserver`, y reiniciar los servicios
que lo consultan.

## Desarrollo sin Docker

Para trabajar con un IDE, se compila y se ejecuta cada módulo por separado
(Java 17). El wrapper Maven `mvnw` descarga lo que falte:

```bash
cd backend/registry        && ./mvnw spring-boot:run
cd backend/config-server   && ./mvnw spring-boot:run
cd backend/auth            && ./mvnw spring-boot:run
cd backend/alumnos         && ./mvnw spring-boot:run
cd backend/administracion  && ./mvnw spring-boot:run
cd backend/gateway         && ./mvnw spring-boot:run
cd backend/admin-server    && ./mvnw spring-boot:run
```

Sin Docker hace falta también un RabbitMQ en `localhost:5672`:

```bash
docker compose up -d rabbitmq
```

## Pruebas

Cada módulo se prueba por separado. Los tests no necesitan ni Config Server,
ni Eureka, ni RabbitMQ: usan valores equivalentes en
`src/test/resources/application.yml` y firman tokens reales con Nimbus para
recorrer la cadena completa (cookie → validación → roles).

```bash
cd backend/auth             && ./mvnw test   # 16 pruebas
cd backend/gateway          && ./mvnw test   # 12 pruebas
cd backend/alumnos          && ./mvnw test   # 19 pruebas
cd backend/administracion   && ./mvnw test   # 19 pruebas
```

Qué cubren, más allá del CRUD:

- El login **no** filtra tokens en el cuerpo de la respuesta.
- El refresh token anterior queda anulado y su reutilización se detecta.
- Un token firmado con otro secreto, de otro emisor o caducado se rechaza.
- Un refresh token **no** sirve como credencial de la API ni para abrir sesión.
- Un `USER` recibe `403` al escribir; un `ADMIN` obtiene `201`.
- Sin token, la respuesta de error es JSON con un mensaje legible.
- La documentación y su tema se sirven **sin sesión**, y cada página apunta a su
  propio JSON (no al de ejemplo de petstore).
- Si el bus de eventos está caído, el alta devuelve `503` y **no** deja el
  registro a medias.

En total, 69 pruebas entre los siete módulos.

## Estructura

```
.
├── docker-compose.yml          # stack completo: 8 servicios
├── README.md
└── backend/
    ├── registry/               # Eureka            :8761
    ├── config-server/          # configuración     :8888
    ├── auth/                   # autenticación     :8100
    ├── alumnos/                # CRUD alumnos      :8101
    ├── administracion/         # CRUD docentes     :8102
    ├── gateway/                # puerta de entrada :8080
    ├── admin-server/           # monitorización    :9090
    └── data/                   # bases de datos H2 (volumen en Docker)
```

## Puesta en marcha paso a paso

1. `docker compose up --build`
2. Abrir http://localhost:8080/auth/docs/index.html
3. `POST /auth/login` con `{"username":"admin","password":"admin123"}`.
   Swagger UI guarda la cookie en el navegador.
4. Ir a http://localhost:8080/api/alumnos/docs/index.html y usar *Try it out*:
   las peticiones ya funcionan sin autorizar nada, porque viaja la cookie.
5. Repetir el login con `user` / `user123` para comprobar que los `POST`
   devuelven `403`.

## Limitaciones conocidas

- `registry` y `config-server` no tienen Spring Security: solo exponen
  `/actuator/health` e `/actuator/info`. Con datos sensibles habría que
  protegerlos.
- El secreto JWT es simétrico y vive en el Config Server. En producción
  conviene un almacén de claves externo o JWT asimétrico (RS256) con rotación.
- Las sesiones del servicio `auth` viven en memoria: al reiniciar ese
  contenedor, las sesiones abiertas se cierran y hay que iniciar sesión otra vez.
- Las bases de datos H2 y el almacén de refresh tokens son para desarrollo y
  pruebas, no para producción.
- Un alta en `alumnos` o `administracion` guarda el registro y publica el evento
  **en la misma transacción**. Si RabbitMQ no está disponible, la transacción se
  deshace y se responde `503` con un mensaje que dice que no se ha guardado nada:
  reintentar es seguro y nunca duplica el registro. Lo que no cubre esta
  solución es el fallo justo en el medio (broker que acepta el evento y luego
  cae antes del commit); para eso, un *outbox* transaccional.
