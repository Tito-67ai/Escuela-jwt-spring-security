package com.example.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de emision de tokens (app.jwt.*).
 *
 * <p>Viven en el Config Server porque este servicio los EMITE y el gateway y los
 * microservicios de negocio los VALIDAN: todos deben leer exactamente los mismos
 * valores o las firmas no quadrarian.</p>
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Secreto de firma de los access tokens (HS256, minimo 32 caracteres). */
    private String secret;

    /** Secreto de firma de los refresh tokens, distinto del de acceso. */
    private String refreshSecret;

    /** Emisor del token, validado por todos los servicios. */
    private String issuer;

    /** Duracion del access token en milisegundos (15 min por defecto). */
    private long expiration;

    /** Duracion del refresh token en milisegundos (8 h por defecto). */
    private long refreshExpiration;

    /**
     * Nombre de la cookie que transporta el access token. Es compartido porque el
     * gateway y los microservicios de negocio tambien tienen que leerla: es la
     * via por la que el navegador autenticado llega hasta la API sin usar la
     * cabecera Authorization.
     */
    private String cookieName = "access_token";

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getRefreshSecret() {
        return refreshSecret;
    }

    public void setRefreshSecret(String refreshSecret) {
        this.refreshSecret = refreshSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        this.expiration = expiration;
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    public void setRefreshExpiration(long refreshExpiration) {
        this.refreshExpiration = refreshExpiration;
    }
}
