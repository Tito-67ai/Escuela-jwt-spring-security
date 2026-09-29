package com.example.auth.service;

import com.example.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Emite y valida los tokens JWT. Este es el unico servicio del sistema con
 * capacidad de crearlos.
 *
 * <p>Se manejan dos tipos de token, firmados con secretos distintos:</p>
 * <ul>
 *   <li><b>access</b>: el que autorizan el gateway y los microservicios. Corto
 *       (15 min) y va en la cookie {@code access_token}.</li>
 *   <li><b>refresh</b>: solo sirve para pedir un access token nuevo. Largo (8 h)
 *       y va en la cookie {@code refresh_token}, restringida a {@code /auth}.</li>
 * </ul>
 *
 * <p>Que los secretos sean distintos evita el "token confusion": aunque alguien
 * se robe el refresh token, no puede usarlo como credencial contra la API,
 * porque se verifica contra otra clave y además lleva {@code typ=refresh}.</p>
 */
@Service
public class JwtService {

    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TIPO = "typ";

    private static final String TIPO_ACCESS = "access";
    private static final String TIPO_REFRESH = "refresh";

    private final SecretKey claveAccess;
    private final SecretKey claveRefresh;
    private final String issuer;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtService(JwtProperties properties) {
        this.claveAccess = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.claveRefresh = Keys.hmacShaKeyFor(properties.getRefreshSecret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.getIssuer();
        this.expirationMs = properties.getExpiration();
        this.refreshExpirationMs = properties.getRefreshExpiration();
    }

    // ------------------------------------------------------------------ emisión

    public String generarAccessToken(String username, List<String> roles) {
        Date ahora = new Date();
        return Jwts.builder()
                .issuer(issuer)
                .subject(username)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TIPO, TIPO_ACCESS)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expirationMs))
                // El algoritmo se fija a proposito: si jjwt lo eligiera solo,
                // firmaria con HS384 segun el largo de la clave y los validadores
                // (HS256) rechazarian el token.
                .signWith(claveAccess, Jwts.SIG.HS256)
                .compact();
    }

    public String generarRefreshToken(String username) {
        Date ahora = new Date();
        return Jwts.builder()
                .issuer(issuer)
                .subject(username)
                .claim(CLAIM_TIPO, TIPO_REFRESH)
                // Identificador unico. Sin el, dos renovaciones dentro del mismo
                // segundo producirian un token IDENTICO (mismo sub, iss, iat y exp),
                // y la rotacion no rotaria nada: el token viejo seguiria valido.
                .id(UUID.randomUUID().toString())
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + refreshExpirationMs))
                .signWith(claveRefresh, Jwts.SIG.HS256)
                .compact();
    }

    /** Vida del access token en segundos (lo que se informa en la respuesta). */
    public long getExpirationSegundos() {
        return expirationMs / 1000;
    }

    // --------------------------------------------------------------- validación

    /** Devuelve el subject del refresh token si es autentico y vigente. */
    public String subjectDelRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            throw new RefreshTokenInvalidoException("No se ha presentado ningun refresh token");
        }
        Claims claims;
        try {
            claims = leerRefresh(token);
        } catch (JwtException | IllegalArgumentException e) {
            // Firma manipulada, token caducado o corrupto: para el cliente es
            // indistinguible de "no hay token".
            throw new RefreshTokenInvalidoException("Refresh token invalido o expirado");
        }
        if (!TIPO_REFRESH.equals(claims.get(CLAIM_TIPO, String.class))) {
            throw new RefreshTokenInvalidoException("El token presentado no es un refresh token");
        }
        return claims.getSubject();
    }

    public List<String> rolesDelAccessToken(String token) {
        Object roles = leerAccess(token).get(CLAIM_ROLES);
        if (roles instanceof List<?> lista) {
            return lista.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Claims leerAccess(String token) {
        return Jwts.parser()
                .verifyWith(claveAccess)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Claims leerRefresh(String token) {
        return Jwts.parser()
                .verifyWith(claveRefresh)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
