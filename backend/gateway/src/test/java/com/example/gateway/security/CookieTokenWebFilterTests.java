package com.example.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * El token debe poder llegar al gateway de dos formas equivalentes: en la cookie
 * HttpOnly que entrega el servicio auth, o en la cabecera Authorization. El
 * filtro se encarga de unificarlas antes de la cadena de seguridad.
 */
class CookieTokenWebFilterTests {

    private final CookieTokenWebFilter filter = new CookieTokenWebFilter("access_token");

    @Test
    void convierteLaCookieEnCabeceraAuthorization() {
        assertEquals("Bearer token-de-la-cookie", cabeceraTrasFiltrar(
                MockServerHttpRequest.get("/api/alumnos")
                        .cookie(new HttpCookie("access_token", "token-de-la-cookie"))));
    }

    @Test
    void respetaUnaCabeceraAuthorizationYaPresente() {
        assertEquals("Bearer token-de-la-cabecera", cabeceraTrasFiltrar(
                MockServerHttpRequest.get("/api/alumnos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-de-la-cabecera")
                        .cookie(new HttpCookie("access_token", "token-de-la-cookie"))));
    }

    @Test
    void noInventaUnaCabeceraSiNoHayTokenNiCookie() {
        assertNull(cabeceraTrasFiltrar(MockServerHttpRequest.get("/api/alumnos")
                .cookie(new HttpCookie("otra_cookie", "irrelevante"))));
    }

    @Test
    void ignoraUnaCookieDeTokenVacia() {
        assertNull(cabeceraTrasFiltrar(MockServerHttpRequest.get("/api/alumnos")
                .cookie(new HttpCookie("access_token", ""))));
    }

    @Test
    void elFiltroSeEjecutaAntesQueLaCadenaDeSeguridad() {
        // Si se ejecutara despues, el token ya habria sido rechazado por no
        // encontrar cabecera Authorization.
        assertEquals(Integer.MIN_VALUE, filter.getOrder());
    }

    /** Pasa la peticion por el filtro y devuelve la cabecera Authorization resultante. */
    private String cabeceraTrasFiltrar(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request.build());
        AtomicReference<ServerWebExchange> resultado = new AtomicReference<>();

        filter.filter(exchange, e -> {
            resultado.set(e);
            return Mono.empty();
        }).block();

        return resultado.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    }
}
