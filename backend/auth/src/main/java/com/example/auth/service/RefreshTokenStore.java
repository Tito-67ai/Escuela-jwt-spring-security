package com.example.auth.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lleva el control de que refresh token sigue siendo valido para cada usuario.
 *
 * <p>Un JWT es autocontenido: si solo se valida la firma, un refresh token
 * robado sirve para siempre hasta que caduca, y se puede reutilizar mil veces.
 * Este registro lo corrige con dos reglas:</p>
 * <ul>
 *   <li><b>Rotacion</b>: al renovar, el refresh token antiguo se invalida y se
 *       emite uno nuevo. Solo hay un refresh token vivo por usuario.</li>
 *   <li><b>Deteccion de replay</b>: si llega un token que no es el vigente, se
 *       da por comprometido y se cierra la sesion entera de ese usuario.</li>
 * </ul>
 *
 * <p>Solo se guarda el hash SHA-256 del token, nunca el token en claro, para que
 * un volcado de memoria no sirva para suplantar a nadie.</p>
 *
 * <p>Es deliberadamente en memoria: al reiniciar el servicio se cierran todas las
 * sesiones, que es el comportamiento seguro por defecto. Con varios replicas
 * habria que mover este registro a Redis.</p>
 */
@Component
public class RefreshTokenStore {

    private record Sesion(String hash, Instant expira) {
        boolean haCaducado(Instant ahora) {
            return ahora.isAfter(expira);
        }
    }

    private final Map<String, Sesion> sesiones = new ConcurrentHashMap<>();

    /** Registra el refresh token recien emitido como el unico valido del usuario. */
    public void registrar(String username, String refreshToken, Instant expira) {
        sesiones.put(username, new Sesion(hash(refreshToken), expira));
    }

    /**
     * @return true si el token es el vigente del usuario.
     * @throws RefreshTokenInvalidoException si ya fue rotado o el usuario no tiene
     *                                       sesion, y en ese caso se cierra la sesion.
     */
    public void exigirVigente(String username, String refreshToken) {
        Sesion sesion = sesiones.get(username);
        Instant ahora = Instant.now();

        if (sesion == null || sesion.haCaducado(ahora)) {
            sesiones.remove(username);
            throw new RefreshTokenInvalidoException("El refresh token ya no esta vigente");
        }
        if (!MessageDigest.isEqual(
                sesion.hash().getBytes(StandardCharsets.UTF_8),
                hash(refreshToken).getBytes(StandardCharsets.UTF_8))) {
            // Token distinto del vigente con la misma identidad: replay. Se cierra
            // la sesion completa para que el atacante y el titular pierdan acceso.
            sesiones.remove(username);
            throw new RefreshTokenInvalidoException(
                    "Refresh token ya rotado: se ha cerrado la sesion por seguridad");
        }
    }

    /** Cierra la sesion (logout). El refresh token deja de servir de inmediato. */
    public void revocar(String username) {
        if (username != null) {
            sesiones.remove(username);
        }
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
