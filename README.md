Arquitectura

El proyecto tiene:

Angular: parte que ve y usa el usuario.
API Gateway: puerta de entrada al backend.
Microservicio Alumnos: se encarga de los alumnos.
Microservicio Administración: se encarga de los docentes.
Eureka: registra y encuentra los microservicios.
Config Server: guarda la configuración.
RabbitMQ: permite mandar mensajes entre microservicios.
JWT + Spring Security: se encargan de la seguridad.
JWT

JWT es un token que sirve para identificar al usuario después de iniciar sesión.

El funcionamiento básico es:

Usuario
   ↓
Login
   ↓
Gateway
   ↓
JWT
   ↓
Angular guarda el token

Después, cuando el usuario quiere acceder a algo:

Angular → JWT → Gateway → Microservicio

El Gateway revisa el token y el microservicio también lo vuelve a revisar.

Access Token y Refresh Token

Hay dos tokens:

Access Token

Dura 15 minutos.
Se usa para acceder a la API.

Refresh Token

Dura 8 horas.
Sirve para conseguir otro Access Token cuando el anterior vence.
Roles

Hay dos roles principales:

ADMIN: puede consultar, crear, modificar y eliminar.
USER: puede consultar, pero no modificar.

Por ejemplo:

GET     → USER y ADMIN
POST    → ADMIN
PUT     → ADMIN
DELETE  → ADMIN

Si un USER intenta hacer un POST, recibe 403 Forbidden.

RabbitMQ

RabbitMQ sirve para que los microservicios se manden mensajes entre ellos.

Por ejemplo:

Alumnos crea un alumno
        ↓
    RabbitMQ
        ↓
Administración recibe el mensaje

Los eventos utilizados son alumno.creado y docente.creado.

Eureka

Eureka es como una agenda de microservicios.

Los servicios se registran ahí para que los demás puedan encontrarlos.

Eureka
 ├── Alumnos
 ├── Administración
 └── Gateway

Está en el puerto 8761.

Config Server

Es el lugar donde se guarda la configuración de los microservicios.

Por ejemplo:

Puertos
Base de datos
RabbitMQ
Eureka
Seguridad
Usuarios

Así se puede centralizar la configuración en un solo lugar.

API Gateway

Es la puerta de entrada al backend.

Angular no llama directamente a los microservicios.

Hace:

Angular
   ↓
Gateway :8080
   ↓
Alumnos :8101
   ↓
Administración :8102

También se encarga de las rutas y parte de la seguridad JWT.

Para memorizar
Angular = interfaz

Gateway = entrada

JWT = identifica al usuario

Spring Security = protege

Eureka = encuentra servicios

Config Server = guarda configuración

RabbitMQ = manda mensajes

Microservicios = hacen las tareas
