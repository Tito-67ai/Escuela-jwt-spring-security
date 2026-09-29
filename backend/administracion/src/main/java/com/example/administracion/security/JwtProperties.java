package com.example.administracion.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de validacion del token, leidos del Config Server (app.jwt.*).
 *
 * <p>Deben ser IDENTICOS a los que usa el servicio auth para firmar: ese es el
 * mecanismo que hace que la seguridad viaje con la peticion y no dependa de pasar
 * (o no) por el gateway.</p>
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Secreto de firma con el que se valida el access token. */
    private String secret;

    /** Emisor del token, validado aqui para rechazar tokens de otra aplicacion. */
    private String issuer;

    /** Nombre de la cookie que transporta el access token. */
    private String cookieName = "access_token";

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

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }
}
