package com.example.administracion.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * El microservicio alumnos como RESOURCE SERVER.
 *
 * <p>No confía en el gateway: valida por su cuenta la firma, el emisor y la
 * caducidad del access token. Asi, aunque alguien conozca el puerto 8101 y llame
 * directo al servicio (saltandose el gateway), la API sigue protegida.</p>
 *
 * <p>Defensa en profundidad, en dos niveles:</p>
 * <ol>
 *   <li><b>Por URL</b>: las reglas de esta clase (GET para cualquier
 *       autenticado, escritura solo ADMIN).</li>
 *   <li><b>Por metodo</b>: @PreAuthorize en el controlador.</li>
 * </ol>
 *
 * <p>El token puede llegar en la cookie HttpOnly o en la cabecera Authorization,
 * asi que un navegador autenticado no necesita enviar nada a mano.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, MonitoringProperties.class})
public class SecurityConfig {

    /**
     * Rutas publicas de la documentacion.
     *
     * <p>La documentacion se publica bajo el prefijo publico del gateway
     * ({@code /api/administracion/**}) y no en la raiz del servicio, porque el
     * gateway reenvia la ruta sin recortarle el prefijo. Este servicio tambien se
     * protege por su cuenta, asi que debe reconocer esas rutas explicitamente; si
     * no, la documentacion pasaria el gateway pero el {@code denyAll()} de abajo la
     * devolveria con un 401 y Swagger UI no abriria nunca.</p>
     *
     * <p>El CSS del tema cuelga de {@code /docs/} y no de la raiz: en la raiz, el
     * {@code @GetMapping("/{id}")} del controller se apropiaba de la ruta e
     * intentaba convertir "swagger-custom.css" en un id numerico.</p>
     */
    private static final String[] DOCUMENTACION = {
            "/api/administracion/v3/api-docs/**", "/api/administracion/swagger-ui/**",
            "/api/administracion/swagger-ui.html", "/api/administracion/docs/**",
            "/api/administracion/favicon.ico",
            // Las formas cortas, por si se llama al servicio directamente en :8102.
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/swagger-custom.css", "/favicon.ico"
    };

    /**
     * Cadena 1: endpoints de actuator. No forman parte de la API de negocio, asi
     * que se autentican con HTTP Basic y la cuenta de monitoreo que usa Spring
     * Boot Admin. Se separan en su propia cadena para no mezclarlos con el JWT.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    /** Cadena 2: la API de negocio, protegida con JWT. */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http,
                                               JwtDecoder jwtDecoder,
                                               JwtAuthenticationConverter jwtAuthenticationConverter,
                                               BearerTokenResolver bearerTokenResolver) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Documentacion interactiva de la API. Se publica sin sesion
                        // a proposito: es la unica "interfaz" del proyecto y tiene que
                        // poder consultarse antes de iniciar sesion. swagger-custom.css
                        // tambien va aqui, o el tema no se aplicaria.
                        .requestMatchers(DOCUMENTACION).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        .requestMatchers("/h2-console/**").hasRole("ADMIN")
                        // Spring reenvia a /error (DispatcherType.ERROR) cuando una
                        // peticion falla por cualquier motivo (400, 404, 500...). Sin
                        // esta excepcion, un error de la app terminaria en el
                        // denyAll() de abajo y se responderia 403 "sin permisos",
                        // enmascarando el error real.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .bearerTokenResolver(bearerTokenResolver)
                        .authenticationEntryPoint(ApiErrors.noAutenticado()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(ApiErrors.noAutenticado())
                        .accessDeniedHandler(ApiErrors.sinPermisos()))
                .build();
    }

    /** El token se acepta en la cabecera Authorization o en la cookie HttpOnly. */
    @Bean
    public BearerTokenResolver bearerTokenResolver(JwtProperties properties) {
        return new CookieOrHeaderBearerTokenResolver(properties.getCookieName());
    }

    /**
     * Valida el access token con el secreto que compartio el servicio auth a
     * traves del Config Server. Comprueba firma, emisor y que sea de tipo acceso.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        return AccessTokenDecoder.create(properties.getSecret(), properties.getIssuer());
    }

    /** Claim "roles" (ADMIN) -> autoridad ROLE_ADMIN, que es lo que usa hasRole(...). */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(rolesConverter);
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(MonitoringProperties properties) {
        MonitoringProperties.MonitoringInfo monitoring = properties.getMonitoring();
        UserDetails usuario = User.withUsername(monitoring.getUsername())
                .password(monitoring.getPassword())
                .roles(monitoring.getRoles().toArray(new String[0]))
                .build();
        return new InMemoryUserDetailsManager(usuario);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // Necesario para que las cookies viajen en peticiones con credenciales.
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Authorization"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
