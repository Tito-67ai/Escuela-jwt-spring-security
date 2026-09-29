package com.example.auth.service;

import com.example.auth.model.TokensEmitidos;

/**
 * Logica de autenticacion: comprobar credenciales y emitir tokens.
 *
 * <p>Es la unica pieza del sistema que sabe como se obtiene un token. El gateway
 * y los microservicios de negocio no la usan: solo validan lo que esta emite.</p>
 */
public interface AuthService {

    /** Comprueba usuario y contrasena y devuelve un par de tokens nuevo. */
    TokensEmitidos login(String username, String rawPassword);

    /** Renueva el access token a partir de un refresh token vigente. */
    TokensEmitidos refrescar(String refreshToken);

    /** Cierra la sesion: el refresh token deja de servir de inmediato. */
    void logout(String username);
}
