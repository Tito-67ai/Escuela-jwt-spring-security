package com.example.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de los tokens que el gateway valida (app.jwt.*).
 *
 * <p>El gateway no emite tokens: los lee. Estos valores se los da el servicio
 * auth a traves del Config Server, y deben coincidir con los suyos o las firmas
 * no quadrarian.</p>
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Secreto de firma con el que se valida el access token. */
    private String secret;

    /** Emisor del token, validado en todos los servicios. */
    private String issuer;

    /** Nombre de la cookie que transporta el access token. */
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

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
