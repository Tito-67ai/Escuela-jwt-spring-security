package com.example.auth.service;

import com.example.auth.config.AuthProperties;
import com.example.auth.config.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Construye las cookies con las que el token viaja al navegador.
 *
 * <p>HttpOnly es la clave de que el frontend no tenga que programar nada: al ser
 * HttpOnly, el JavaScript de la pagina no puede leer el token, asi que no puede
 * filtrarlo en un XSS ni guardarlo en localStorage. El navegador la envia sola
 * en cada peticion y Spring Security la valida igual que una cabecera.</p>
 */
@Component
public class TokenCookieManager {

    private final AuthProperties.Cookie config;
    private final JwtProperties jwtProperties;

    public TokenCookieManager(AuthProperties properties, JwtProperties jwtProperties) {
        this.config = properties.getCookie();
        this.jwtProperties = jwtProperties;
    }

    /** Vida de la cookie del access token: la del propio token. */
    public Duration duracionAccess() {
        return Duration.ofMillis(jwtProperties.getExpiration());
    }

    /** Vida de la cookie del refresh token: la del propio token. */
    public Duration duracionRefresh() {
        return Duration.ofMillis(jwtProperties.getRefreshExpiration());
    }

    /** Cookie del access token, con Path=/ para que alcance a toda la API. */
    public ResponseCookie crearAccess(String token, Duration ttl) {
        return base(jwtProperties.getCookieName(), token, ttl, "/");
    }

    /**
     * Cookie del refresh token, con Path=/auth: el navegador solo la envia a los
     * endpoints de autenticacion, nunca a los de negocio.
     */
    public ResponseCookie crearRefresh(String token, Duration ttl) {
        return base(config.getRefreshName(), token, ttl, "/auth");
    }

    /** Cookie de caducidad inmediata. Path identico al de origen: si no, no se borra. */
    public ResponseCookie borrarAccess() {
        return base(jwtProperties.getCookieName(), "", Duration.ZERO, "/");
    }

    public ResponseCookie borrarRefresh() {
        return base(config.getRefreshName(), "", Duration.ZERO, "/auth");
    }

    private ResponseCookie base(String name, String value, Duration ttl, String path) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .path(path)
                .maxAge(ttl)
                .sameSite(config.getSameSite());
        if (config.isSecure()) {
            builder.secure(true);
        }
        if (config.getDomain() != null && !config.getDomain().isBlank()) {
            builder.domain(config.getDomain());
        }
        return builder.build();
    }
}
