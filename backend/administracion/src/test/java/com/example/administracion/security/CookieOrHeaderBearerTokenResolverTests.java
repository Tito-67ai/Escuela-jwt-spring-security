package com.example.administracion.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * El token debe poder llegar a la API de dos formas equivalentes: en la cookie
 * HttpOnly que entrega el servicio auth, o en la cabecera Authorization.
 */
class CookieOrHeaderBearerTokenResolverTests {

    private final CookieOrHeaderBearerTokenResolver resolver =
            new CookieOrHeaderBearerTokenResolver("access_token");

    @Test
    void leeElTokenDeLaCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/administracion");
        request.setCookies(new Cookie("access_token", "token-de-la-cookie"));

        assertEquals("token-de-la-cookie", resolver.resolve(request));
    }

    @Test
    void leeElTokenDeLaCabeceraAuthorization() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/administracion");
        request.addHeader("Authorization", "Bearer token-de-la-cabecera");

        assertEquals("token-de-la-cabecera", resolver.resolve(request));
    }

    @Test
    void laCabeceraGanaSiVienenLasDos() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/administracion");
        request.addHeader("Authorization", "Bearer token-de-la-cabecera");
        request.setCookies(new Cookie("access_token", "token-de-la-cookie"));

        assertEquals("token-de-la-cabecera", resolver.resolve(request));
    }

    @Test
    void devuelveNullSiNoHayNiTokenNiCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/administracion");
        request.setCookies(new Cookie("otra_cookie", "irrelevante"));

        assertNull(resolver.resolve(request));
    }

    @Test
    void ignoraUnaCookieDeTokenVacia() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/administracion");
        request.setCookies(new Cookie("access_token", ""));

        assertNull(resolver.resolve(request));
    }
}
