package com.example.auth.model;

import java.util.List;

/**
 * Par de tokens recien emitidos.
 *
 * <p>Es un objeto interno del dominio: el token viaja aqui solo para que el
 * controlador lo meta en las cookies. Lo que sale por HTTP es
 * {@link com.example.auth.dto.TokenResponse}, que no lo incluye.</p>
 *
 * @param username     usuario al que pertenecen
 * @param roles        roles que se incrustan en el access token
 * @param accessToken  token de acceso (cookie access_token)
 * @param refreshToken token de renovacion (cookie refresh_token)
 * @param expiresIn    vida del access token en segundos
 */
public record TokensEmitidos(String username, List<String> roles, String accessToken,
                             String refreshToken, long expiresIn) {
}
