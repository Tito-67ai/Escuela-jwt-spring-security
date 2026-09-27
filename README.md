# Frontend Angular — Escuela con Spring Security + JWT

Esta rama contiene **solo el frontend** (Angular 22). El backend con Spring Security y JWT
vive en la rama [`backend`](https://github.com/Tito-67ai/Escuela-jwt-spring-security/tree/backend),
y el proyecto completo y ejecutable en [`main`](https://github.com/Tito-67ai/Escuela-jwt-spring-security/tree/main).

## Requisitos

- Node.js 18.19+ (npm)
- El backend levantado en `http://localhost:8080` (ver la rama `backend`)

## Puesta en marcha

```powershell
cd frontend
npm install
npm start        # http://localhost:4200
```

El frontend consume **siempre el API Gateway** (`http://localhost:8080`), nunca los
microservicios directamente.

## Sesión y tokens

| Aspecto | Detalle |
|---|---|
| Login | Vista **Iniciar sesión**: `admin`/`admin123` (ADMIN) o `user`/`user123` (USER) |
| Almacenamiento | `accessToken`, `refreshToken`, `username`, `roles` y expiración en `localStorage` |
| Renovación | Un interceptor renueva el access token **antes** de que caduque y reintenta una vez tras un `401` |
| Refresh inválido | Si el refresh también falla, se borra la sesión y se vuelve al login |
| Rutas protegidas | `authGuard` para la sesión y `roleGuard('ADMIN')` para `/seguridad` |

### Qué ve cada rol

| | ADMIN | USER |
|---|---|---|
| Listar alumnos y docentes | Sí | Sí |
| Formularios de alta / edición / borrado | Sí | **No** (ni botón ni formulario) |
| Pestaña **Seguridad** (`/seguridad`) | Sí | **No** (guard de rol) |
| Panel con claims, expiración y renovación manual | Sí | **No** |

La pestaña **Seguridad** decodifica el access token y muestra `sub`, `iss`, `roles`, `iat`,
`exp`, la cuenta atrás para la expiración y el botón para pedir un token nuevo en
`POST /auth/refresh`.

## Archivos clave

| Archivo | Responsabilidad |
|---|---|
| `src/app/services/auth.service.ts` | Login, logout, refresh, roles, expiración, `/auth/me` |
| `src/app/auth.interceptor.ts` | Inyecta `Authorization: Bearer`, renueva y reintenta, limpia la sesión al fallar |
| `src/app/auth.guard.ts` | `authGuard` y `roleGuard(...)` de las rutas |
| `src/app/seguridad/` | Vista de gestión de sesión (solo ADMIN) |
| `src/app/app.routes.ts` | Rutas, guards y redirección al login |

## Notas

- El backend valida CORS con `*`. Si una petición queda cacheada, un hard reload
  (`Ctrl+Shift+R`) lo resuelve.
- Las credenciales son de demostración y vienen del Config Server del backend.
