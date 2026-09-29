package com.example.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * La documentacion interactiva (Swagger UI) es la unica interfaz del proyecto,
 * asi que tiene que cargarse SIN sesion. Si estas rutas exigieran token no habria
 * forma de abrir el "Try it out" para hacer el propio login.
 *
 * <p>En este test no hay microservicios destino, asi que una ruta autorizada
 * falla al enrutar (503) en lugar de responder. Eso permite distinguir las dos
 * situaciones sin depender de Eureka:</p>
 * <ul>
 *   <li>401 o 403 -&gt; el gateway ha bloqueado la peticion: la regla falla.</li>
 *   <li>otro codigo -&gt; la peticion ha pasado la seguridad del gateway.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayDocumentationTests {

    @Autowired
    private WebTestClient webTestClient;

    // ------------------------------------------- la documentacion no pide token

    @Test
    void laDocumentacionDelServicioAuthNoExigeSesion() {
        comprobarQueNoEstaBloqueada("/auth/swagger-ui.html");
        comprobarQueNoEstaBloqueada("/auth/swagger-ui/index.html");
        comprobarQueNoEstaBloqueada("/auth/v3/api-docs");
        comprobarQueNoEstaBloqueada("/auth/docs/swagger-custom.css");
    }

    @Test
    void laDocumentacionDeLosMicroserviciosNoExigeSesion() {
        comprobarQueNoEstaBloqueada("/api/alumnos/swagger-ui.html");
        comprobarQueNoEstaBloqueada("/api/alumnos/v3/api-docs");
        comprobarQueNoEstaBloqueada("/api/alumnos/docs/swagger-custom.css");
        comprobarQueNoEstaBloqueada("/api/administracion/swagger-ui.html");
        comprobarQueNoEstaBloqueada("/api/administracion/docs/swagger-custom.css");
    }

    // ------------------------------------- y el resto de la API sigue protegida

    @Test
    void laApiDeNegocioSigueExigiendoToken() {
        webTestClient.get().uri("/api/alumnos")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void elPerfilSigueExigiendoToken() {
        webTestClient.get().uri("/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void actuatorSalvoHealthSigueExigiendoPermisos() {
        webTestClient.get().uri("/actuator/env")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void healthSigueSiendoPublico() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * Falla si la respuesta es 401 o 403, es decir, si el gateway ha pedido
     * credenciales para una ruta de documentacion que deberia ser publica.
     */
    private void comprobarQueNoEstaBloqueada(String uri) {
        HttpStatus status = (HttpStatus) webTestClient.get().uri(uri)
                .exchange()
                .returnResult(Void.class)
                .getStatus();
        org.junit.jupiter.api.Assertions.assertNotEquals(HttpStatus.UNAUTHORIZED, status,
                "El gateway exige sesion para " + uri + ": la documentacion dejaria de ser publica");
        org.junit.jupiter.api.Assertions.assertNotEquals(HttpStatus.FORBIDDEN, status,
                "El gateway rechaza " + uri + ": la documentacion dejaria de ser publica");
    }
}
