package com.example.alumnos.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Construye el validador de access tokens de este servicio.
 *
 * <p>Se comproban tres cosas, no solo la firma:</p>
 * <ol>
 *   <li>La firma HS256 con el secreto compartido.</li>
 *   <li>El emisor, para no aceptar tokens de otra aplicacion.</li>
 *   <li>El tipo: <b>solo</b> valen los tokens marcados {@code typ=access}.</li>
 * </ol>
 *
 * <p>El punto 3 es defensa en profundidad. Hoy un refresh token ya queda
 * rechazado porque se firma con otro secreto, pero si ambas claves llegaran a ser
 * iguales por un error de configuracion, este chequeo seguiria impidiendo que un
 * refresh token (de 8 horas) se usara como credencial de acceso.</p>
 */
public final class AccessTokenDecoder {

    private static final String TIPO_ACCESS = "access";

    private AccessTokenDecoder() {
    }

    /** Decoder de access tokens HS256, con validacion de firma, emisor y tipo. */
    public static JwtDecoder create(String secret, String issuer) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                soloAccessTokens()));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> soloAccessTokens() {
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
