package com.example.administracion.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Respuestas de error de Spring Security en JSON.
 *
 * <p>Por defecto estos dos filtros devuelven un 401 o un 403 sin cuerpo, que no
 * dicen nada. Aqui se contesta siempre {@code {status, error, message}} con un
 * mensaje accionable, igual que en el resto de servicios.</p>
 */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static AuthenticationEntryPoint noAutenticado() {
        return (request, response, ex) -> escribir(response, HttpStatus.UNAUTHORIZED,
                "Token ausente, invalido o expirado. Inicie sesion otra vez.");
    }

    public static AccessDeniedHandler sinPermisos() {
        return (request, response, ex) -> escribir(response, HttpStatus.FORBIDDEN,
                "No tiene permisos para esta operacion.");
    }

    private static void escribir(HttpServletResponse response, HttpStatus status, String mensaje)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + mensaje + "\"}");
    }
}
