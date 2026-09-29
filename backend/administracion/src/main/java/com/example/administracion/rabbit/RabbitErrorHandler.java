package com.example.administracion.rabbit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce un fallo de RabbitMQ a una respuesta util.
 *
 * <p>Sin esto, un broker apagado devolvia un 500 generico que no decia nada: el
 * cliente no podia saber si el alta se habia guardado o no, y por tanto si
 * reintentar era seguro. Aqui se responde 503 con un mensaje que lo deja claro.</p>
 *
 * <p>El alta va en la misma transaccion que la publicacion (ver
 * {@code DocenteController#crear}), asi que cuando se devuelve este error no hay
 * ningun docente guardado: el reintento no puede duplicar nada.</p>
 */
@RestControllerAdvice
public class RabbitErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(RabbitErrorHandler.class);

    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<Map<String, Object>> brokerNoDisponible(AmqpException ex) {
        log.error("RabbitMQ no disponible al publicar un evento; el alta se deshace", ex);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        cuerpo.put("error", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        cuerpo.put("message", "RabbitMQ no esta disponible, asi que no se ha podido publicar "
                + "el evento. No se ha guardado nada: puede reintentar la operacion.");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .body(cuerpo);
    }
}
