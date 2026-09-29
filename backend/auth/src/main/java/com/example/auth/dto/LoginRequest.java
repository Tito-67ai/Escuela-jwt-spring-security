package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Credenciales que envia POST /auth/login. */
public record LoginRequest(
        @NotBlank(message = "el usuario es obligatorio") String username,
        @NotBlank(message = "la contrasena es obligatoria") String password) {
}
