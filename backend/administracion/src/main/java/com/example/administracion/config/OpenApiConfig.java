package com.example.administracion.config;

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
 * forma de consultar y probar la API a mano con el boton Try it out.</p>
 *
 * <p>La interfaz esta pensada para el navegador: al iniciar sesion en el
 * servicio auth, la cookie se envia sola y estas peticiones funcionan sin
 * autorizar nada. El esquema "bearerAuth" es para curl o Postman.</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI administracionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Microservicio de administracion")
                        .version("1.0.0")
                        .description("""
                                Gestion del personal del centro: docentes y administrativos.

                                **Como autenticarse**

                                1. Se inicia sesion en el servicio `auth` (POST /auth/login). Esa
                                   llamada deja una cookie HttpOnly en el navegador.
                                2. Como las peticiones salen del mismo host, el navegador envia esa
                                   cookie automaticamente y estas peticiones ya funcionan.
                                3. Para curl o Postman: *Authorize* con el access token, que viaja
                                   en la cabecera `Authorization: Bearer`.

                                **Permisos**

                                | Rol    | Leer (GET) | Escribir (POST, PUT, DELETE) |
                                |--------|------------|------------------------------|
                                | `USER` | si         | no                           |
                                | `ADMIN`| si         | si                           |
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
