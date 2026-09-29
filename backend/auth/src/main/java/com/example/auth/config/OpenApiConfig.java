package com.example.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacion interactiva de la API (Swagger UI en /swagger-ui.html).
 *
 * <p>Es la "parte visual" del servicio: sin frontend, la documentacion es la
 * forma de probar la API a mano con el boton Try it out.</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Servicio de autenticacion")
                        .version("1.0.0")
                        .description("""
                                Emite y renueva los tokens JWT del sistema.

                                **Como autenticarse**

                                1. `POST /auth/login` con usuario y contrasena. La respuesta no
                                   incluye el token: lo deja en dos cookies HttpOnly
                                   (`access_token` y `refresh_token`).
                                2. A partir de ahi el navegador envia la cookie solo. No hay que
                                   guardar nada en JavaScript ni mandar cabeceras a mano.
                                3. `POST /auth/refresh` renueva el acceso cuando caduque el token.
                                4. `POST /auth/logout` cierra la sesion y borra las cookies.

                                **Clientes de linea de comandos**

                                El esquema "bearerAuth" de abajo sirve para curl o Postman, que no
                                gestionan cookies: al hacer *Authorize* se pega ahi el access token
                                y la peticion lo envia en la cabecera `Authorization: Bearer`.

                                **Usuarios de prueba**

                                | Usuario  | Contrasena | Rol   |
                                |----------|------------|-------|
                                | `admin`  | `admin123` | ADMIN |
                                | `user`   | `user123`  | USER  |
                                """)
                        .license(new License().name("Uso academico")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token para clientes que no gestionan cookies")));
    }
}
