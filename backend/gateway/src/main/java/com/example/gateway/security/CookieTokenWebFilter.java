package com.example.gateway.security;

import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Convierte la cookie del access token en una cabecera Authorization.
 *
 * <p>Un navegador autenticado no manda cabeceras: manda la cookie HttpOnly que
 * le entrego el servicio auth. Spring Security solo sabe leer el token de la
 * cabecera, asi que este filtro hace de puente: si la peticion no trae
 * Authorization pero trae la cookie {@code access_token}, se la anade.</p>
 *
 * <p>Es lo que hace que ningun cliente tenga que programar nada. Un cliente de
 * linea de comandos sigue mandando {@code Authorization: Bearer ...} y este
 * filtro ni lo mira.</p>
 *
 * <p>Va antes que toda la cadena de seguridad (de ahi el orden maximo), porque si
 * se ejecutara despues el token ya habria sido rechazado.</p>
 */
public class CookieTokenWebFilter implements WebFilter, Ordered {

    private final String cookieName;

    public CookieTokenWebFilter(String cookieName) {
        this.cookieName = cookieName;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION) != null) {
            return chain.filter(exchange);
        }

        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(cookieName);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest conToken = exchange.getRequest().mutate()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cookie.getValue())
                .build();
        return chain.filter(exchange.mutate().request(conToken).build());
    }
}
