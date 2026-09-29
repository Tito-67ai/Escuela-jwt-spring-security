package com.example.alumnos.security;

import com.example.alumnos.rabbit.AlumnoPublisher;
import com.example.alumnos.repository.AlumnoRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba la matriz de permisos de la API de alumnos tal y como la veria un
 * cliente real, con tokens firmados de verdad.
 *
 * <p>Se firman aqui con el mismo secreto que declararia el Config Server, para
 * que la prueba atraviese de extremo a extremo la cadena: cookie -&gt; resolutor
 * -&gt; JwtDecoder -&gt; claim roles -&gt; reglas de autorizacion.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AlumnoSecurityTests {

    private static final String SECRETO = "clave-de-pruebas-access-token-colegio-microservicios-larga";
    private static final String EMISOR = "colegio-microservicios";

    @Autowired
    private MockMvc mockMvc;

    /** Se apaga el publicador: aqui se prueba seguridad, no RabbitMQ. */
    @MockitoBean
    private AlumnoPublisher publisher;

    @Autowired
    private AlumnoRepository repository;

    // ------------------------------------------------------------------ lectura

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/alumnos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        "Token ausente, invalido o expirado. Inicie sesion otra vez."));
    }

    @Test
    void conTokenEnLaCookieSePermiteLeer() throws Exception {
        mockMvc.perform(get("/api/alumnos").cookie(cookieConToken("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void conTokenEnLaCabeceraTambienSePermiteLeer() throws Exception {
        mockMvc.perform(get("/api/alumnos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("USER")))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- escritura

    @Test
    void unUsuarioNormalNoPuedeCrearAlumnos() throws Exception {
        mockMvc.perform(post("/api/alumnos")
                        .cookie(cookieConToken("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana","apellido":"Gomez"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No tiene permisos para esta operacion."));
    }

    @Test
    void unAdministradorSiPuedeCrearAlumnos() throws Exception {
        mockMvc.perform(post("/api/alumnos")
                        .cookie(cookieConToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana","apellido":"Gomez","dni":"12345678"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Ana"));
    }

    // ------------------------------------------------------- alta y RabbitMQ

    @Test
    void siElBrokerEstaApagadoNoSeGuardaElAlumno() throws Exception {
        // El fallo que se arrastraba antes: el alta se confirmaba y luego reventaba
        // la publicacion, con lo que el cliente recibia un 500 con el alumno ya en
        // la base de datos, y un reintento lo duplicaba. Con el alta y la
        // publicacion en la misma transaccion, si el broker no esta, no se guarda
        // nada y el reintento es seguro.
        long antes = repository.count();
        doThrow(new AmqpConnectException(new ConnectException("broker apagado")))
                .when(publisher).publicarAlumnoCreado(any());

        mockMvc.perform(post("/api/alumnos")
                        .cookie(cookieConToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Broker","apellido":"Caido","dni":"99999999"}"""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "RabbitMQ no esta disponible, asi que no se ha podido publicar "
                                + "el evento. No se ha guardado nada: puede reintentar la operacion."));

        assertThat(repository.count()).isEqualTo(antes);
    }

    // ------------------------------------------------------- tokens rechazados

    @Test
    void unTokenFirmadoConOtroSecretoSeRechaza() throws Exception {
        String tokenAjeno = firmar("ADMIN", EMISOR, "clave-ajena-del-todo-diferente-y-larguisima");

        mockMvc.perform(get("/api/alumnos").cookie(new Cookie("access_token", tokenAjeno)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenDeOtraAplicacionSeRechazaPorElEmisor() throws Exception {
        String tokenAjeno = firmar("ADMIN", "otra-aplicacion-cualquiera", SECRETO);

        mockMvc.perform(get("/api/alumnos").cookie(new Cookie("access_token", tokenAjeno)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenCaducadoSeRechaza() throws Exception {
        String caducado = firmarConCaducidad("ADMIN", EMISOR, SECRETO, -60_000);

        mockMvc.perform(get("/api/alumnos").cookie(new Cookie("access_token", caducado)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unRefreshTokenNoSirveComoCredencialDeLaApi() throws Exception {
        // Va firmado con el secreto de acceso y es valido, pero declara typ=refresh.
        String refreshComoAccess = firmarConTipo("refresh");

        mockMvc.perform(get("/api/alumnos").cookie(new Cookie("access_token", refreshComoAccess)))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void laDocumentacionEsPublicaParaPoderConsultarlaSinSesion() throws Exception {
        mockMvc.perform(get("/api/alumnos/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void elTemaVisualDeSwaggerEsPublico() throws Exception {
        // Si este CSS exigiera sesion, Swagger UI abriria sin estilo: la
        // documentacion es la unica interfaz del proyecto y debe verse siempre
        // igual, haya sesion o no.
        mockMvc.perform(get("/api/alumnos/docs/swagger-custom.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/css")));
    }

    @Test
    void elInicializadorDeSwaggerCargaElJsonYElTemaDesdeLasRutasPublicas() throws Exception {
        // Esta es la entrada real a la documentacion. El navegador solo pinta la
        // API si la pagina apunta al JSON publico de este servicio, y solo sale
        // con el tema del proyecto si lo carga. Si alguno de los dos faltara, se
        // abriria en blanco o con el estilo de fabrica.
        mockMvc.perform(get("/api/alumnos/docs/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "url: \"/api/alumnos/v3/api-docs\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/api/alumnos/docs/swagger-custom.css\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"/api/alumnos/swagger-ui/swagger-ui-bundle.js\"")));
    }

    // ------------------------------------------------------------- auxiliares

    private Cookie cookieConToken(String rol) throws Exception {
        return new Cookie("access_token", token(rol));
    }

    private String token(String rol) throws Exception {
        return firmar(rol, EMISOR, SECRETO);
    }

    private String firmar(String rol, String emisor, String secreto) throws Exception {
        return firmarConCaducidad(rol, emisor, secreto, 600_000);
    }

    private String firmarConCaducidad(String rol, String emisor, String secreto, long vidaMilis)
            throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(emisor)
                .subject("usuario-de-prueba")
                .claim("roles", List.of(rol))
                .claim("typ", "access")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + vidaMilis))
                .jwtID(UUID.randomUUID().toString())
                .build();
        return firmar(claims, secreto);
    }

    private String firmarConTipo(String tipo) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(EMISOR)
                .subject("usuario-de-prueba")
                .claim("roles", List.of("ADMIN"))
                .claim("typ", tipo)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 600_000))
                .build();
        return firmar(claims, SECRETO);
    }

    private String firmar(JWTClaimsSet claims, String secreto) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secreto.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
