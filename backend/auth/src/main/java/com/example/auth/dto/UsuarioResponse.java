package com.example.auth.dto;

import java.util.List;

/**
 * Respuesta de GET /auth/me: quien esta autenticado en este momento.
 *
 * @param authenticated siempre true: si no lo estuviera, la peticion habria
 *                       respondido 401 y nunca llegaria aqui
 * @param username      sujeto del token
 * @param roles         roles que viajan en el token
 */
public record UsuarioResponse(boolean authenticated, String username, List<String> roles) {
}
