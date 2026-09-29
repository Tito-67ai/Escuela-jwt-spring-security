package com.example.auth.dto;

/**
 * Peticion de POST /auth/refresh.
 *
 * <p>El refresh token puede venir en el cuerpo (para clientes de API como curl o
 * Postman) o en la cookie HttpOnly (para el navegador). El controlador lo resuelve
 * por las dos vias, asi que este campo es opcional.</p>
 */
public record RefreshRequest(String refreshToken) {
}
