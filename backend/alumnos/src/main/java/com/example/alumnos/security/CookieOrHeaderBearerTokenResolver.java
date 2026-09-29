package com.example.alumnos.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Acepta el token de dos maneras: cabecera Authorization o cookie.
 *
 * <p>Es lo que hace que ningun cliente tenga que programar nada. El navegador
 * autenticado no envia cabeceras: envia la cookie HttpOnly que le entrego el
 * servicio auth, y Spring Security la lee aqui exactamente igual que leeria un
 * Bearer token. Un cliente de linea de comandos sigue mandando
 * {@code Authorization: Bearer ...} sin cambios.</p>
 *
 * <p>La cabecera tiene prioridad: si un cliente envia las dos, manda la
 * explicita.</p>
 */
public class CookieOrHeaderBearerTokenResolver implements BearerTokenResolver {

    private final String cookieName;
    private final BearerTokenResolver cabecera = new DefaultBearerTokenResolver();

    public CookieOrHeaderBearerTokenResolver(String cookieName) {
        this.cookieName = cookieName;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String desdeCabecera = cabecera.resolve(request);
        if (desdeCabecera != null) {
            return desdeCabecera;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())
                    && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
