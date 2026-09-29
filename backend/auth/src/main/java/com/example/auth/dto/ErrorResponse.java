package com.example.auth.dto;

/**
 * Cuerpo de error de la API, con la misma forma en todos los servicios del
 * sistema: { "status", "error", "message" }.
 */
public record ErrorResponse(int status, String error, String message) {
}
