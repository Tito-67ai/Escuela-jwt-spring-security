package com.example.auth.service;

/** El usuario no existe, esta deshabilitado, o la contrasena no coincide. */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException(String message) {
        super(message);
    }
}
