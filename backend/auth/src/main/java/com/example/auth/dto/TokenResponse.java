package com.example.auth.dto;

import java.util.List;

/**
 * Respuesta de un login o de una renovacion correctos.
 *
 * <p>Nunca incluye el token: este viaja unicamente en la cookie HttpOnly. Asi el
 * navegador no lo expone a JavaScript y, al mismo tiempo, un cliente de linea de
 * comandos puede seguir leyendolo de la cookie con un fichero de cookies.</p>
 *
 * @param username  usuario autenticado
 * @param roles     roles asignados, sin el prefijo ROLE_
 * @param expiresIn vida del access token en segundos
 */
public record TokenResponse(String username, List<String> roles, long expiresIn) {
}
