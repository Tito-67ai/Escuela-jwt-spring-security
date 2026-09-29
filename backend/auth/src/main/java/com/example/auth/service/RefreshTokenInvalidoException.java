package com.example.auth.service;

/**
 * El refresh token no sirve: caduco, tiene la firma manipulada, ya se uso para
 * rotar (replay) o el usuario fue dado de baja.
 */
public class RefreshTokenInvalidoException extends RuntimeException {

    public RefreshTokenInvalidoException(String message) {
        super(message);
    }
}
