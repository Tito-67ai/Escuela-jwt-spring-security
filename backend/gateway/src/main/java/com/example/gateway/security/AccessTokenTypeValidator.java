package com.example.gateway.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rechaza cualquier token que no declare {@code typ=access}.
 *
 * <p>El servicio auth firma los refresh tokens con HS256, pero con OTRO secreto,
 * asi que hoy ya fallarian al validar la firma. Este chequeo es defensa en
 * profundidad: si ambas claves llegaran a coincidir por un error de
 * configuracion, seguiria impidiendo que un refresh token (de varias horas) se
 * aceptara como credencial de acceso.</p>
 */
final class AccessTokenTypeValidator {

    private static final String TIPO_ACCESS = "access";

    private AccessTokenTypeValidator() {
    }

    static OAuth2TokenValidator<Jwt> soloAccessTokens() {
        return token -> {
            Object tipo = token.getClaims().get("typ");
            if (tipo != null && TIPO_ACCESS.equals(tipo.toString())) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "El token presentado no es un access token",
                    null));
        };
    }
}
