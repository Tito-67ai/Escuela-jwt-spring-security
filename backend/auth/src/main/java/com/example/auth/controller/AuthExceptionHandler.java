package com.example.auth.controller;

import com.example.auth.dto.ErrorResponse;
import com.example.auth.service.CredencialesInvalidasException;
import com.example.auth.service.RefreshTokenInvalidoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de autenticacion a respuestas JSON.
 *
 * <p>Los mensajes son deliberadamente genericos: responder "usuario inexistente"
 * o "contrasena incorrecta" por separado permitiria enumerar que cuentas existen.</p>
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> credencialesInvalidas(CredencialesInvalidasException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(RefreshTokenInvalidoException.class)
    public ResponseEntity<ErrorResponse> refreshInvalido(RefreshTokenInvalidoException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> cuerpoInvalido(MethodArgumentNotValidException e) {
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("peticion no valida");
        return error(HttpStatus.BAD_REQUEST, mensaje);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), mensaje));
    }
}
