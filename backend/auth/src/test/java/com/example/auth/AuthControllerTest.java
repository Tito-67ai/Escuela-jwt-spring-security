package com.example.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba el recorrido completo de autenticacion por HTTP: login, cookies
 * HttpOnly, renovacion con rotacion, deteccion de replay, logout y acceso a
 * /auth/me.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ------------------------------------------------------------------ login

    @Test
    void loginValidoEntregaLosTokensEnCookiesHttpOnlyYNoEnElCuerpo() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin123"}"""))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().path("access_token", "/"))
                // El refresh token queda confinado a /auth: no viaja a la API.
                .andExpect(cookie().path("refresh_token", "/auth"))
                .andExpect(cookie().secure("access_token", false))
                .andExpect(cookie().maxAge("access_token", 900))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles").value(hasItem("ADMIN")))
                .andExpect(jsonPath("$.expiresIn").value(900))
                // El cuerpo jamas lleva el token: solo viaja en la cookie.
                .andExpect(content().string(not(containsString("eyJ"))));
    }

    @Test
    void loginConContrasenaInvalidaResponde401SinFiltrarSiElUsuarioExiste() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"no-es-la-clave"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("access_token"));
    }

    @Test
    void loginConUsuarioInexistenteRespondeElMismoMensajeQueConContrasenaMala() throws Exception {
        String conUsuarioMalo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"no-existe","password":"admin123"}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String conContrasenaMala = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"no-es-la-clave"}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Mismo texto: no se puede deducir que cuentas existen probando.
        org.junit.jupiter.api.Assertions.assertEquals(conUsuarioMalo, conContrasenaMala);
    }

    @Test
    void loginSinCamposDevuelve400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- refresh

    @Test
    void refreshRenuevaElTokenYAnulaElAnterior() throws Exception {
        String primerRefresh = refreshTrasLogin("admin", "admin123");

        // El refresh nuevo vale...
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", primerRefresh)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().value("refresh_token", not(primerRefresh)));

        // ...y el viejo ya no: se detectaria el replay.
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", primerRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshAceptaTambienElTokenEnElCuerpoParaClientesDeApi() throws Exception {
        String refreshToken = refreshTrasLogin("user", "user123");

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user"));
    }

    @Test
    void refreshSinTokenResponde401() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------- me

    @Test
    void meDevuelveLaIdentidadQueViajaEnLaCookie() throws Exception {
        String accessToken = accessTrasLogin("admin", "admin123");

        mockMvc.perform(get("/auth/me").cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles").value(hasItem("ADMIN")));
    }

    @Test
    void meSinTokenResponde401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elRefreshTokenNoSirveParaAbrirSesionEnMe() throws Exception {
        // El refresh token se firma con otro secreto, asi que ya deberia fallar la
        // validacion. Ademas se comprueba que declare typ=access.
        String refreshToken = refreshTrasLogin("admin", "admin123");

        mockMvc.perform(get("/auth/me").cookie(new Cookie("access_token", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elAccessTokenTambienSeAceptaEnLaCabeceraAuthorization() throws Exception {
        String accessToken = accessTrasLogin("user", "user123");

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user"));
    }

    // ------------------------------------------------------------- documentacion

    @Test
    void laDocumentacionEsPublicaParaPoderConsultarlaSinSesion() throws Exception {
        mockMvc.perform(get("/auth/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void elTemaVisualDeSwaggerEsPublico() throws Exception {
        // Si este CSS exigiera sesion, Swagger UI abriria sin estilo: la
        // documentacion es la unica interfaz del proyecto y debe verse siempre
        // igual, haya sesion o no.
        mockMvc.perform(get("/auth/docs/swagger-custom.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/css")));
    }

    @Test
    void elInicializadorDeSwaggerCargaElJsonYElTemaDesdeLasRutasPublicas() throws Exception {
        // Esta es la entrada real a la documentacion. El navegador solo pinta la
        // API si la pagina apunta al JSON publico de este servicio, y solo sale
        // con el tema del proyecto si lo carga. Si alguno de los dos faltara, se
        // abriria en blanco o con el estilo de fabrica.
        mockMvc.perform(get("/auth/docs/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "url: \"/auth/v3/api-docs\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/auth/docs/swagger-custom.css\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"/auth/swagger-ui/swagger-ui-bundle.js\"")));
    }

    // ---------------------------------------------------------------- logout

    @Test
    void logoutBorraLasCookiesYRevocaElRefreshToken() throws Exception {
        String refreshToken = refreshTrasLogin("admin", "admin123");
        Cookie accessCookie = new Cookie("access_token", accessTrasLogin("admin", "admin123"));

        mockMvc.perform(post("/auth/logout")
                        .cookie(accessCookie, new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("access_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0));

        // Tras el logout, el refresh token ya no sirve ni aunque se repita.
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------- auxiliares

    /** Hace login y devuelve el valor de la cookie indicada. */
    private String cookieTrasLogin(String usuario, String password, String nombre) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + usuario + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn().getResponse().getCookie(nombre)
                .getValue();
    }

    private String accessTrasLogin(String usuario, String password) throws Exception {
        return cookieTrasLogin(usuario, password, "access_token");
    }

    private String refreshTrasLogin(String usuario, String password) throws Exception {
        return cookieTrasLogin(usuario, password, "refresh_token");
    }
}
