package com.example.gateway.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Seguridad del API Gateway (WebFlux / reactiva).
 *
 * <p>El gateway ya NO autentica a nadie: no tiene usuarios ni emite tokens. Solo
 * cumple dos funciones, y ambas delegan en el servicio auth:</p>
 * <ol>
 *   <li><b>Repasar</b> /auth/** al servicio auth, que es el unico que comprueba
 *       credenciales y firma tokens.</li>
 *   <li><b>Autorizar</b>: validar la firma del token que llega (de la cookie o de
 *       la cabecera) y aplicar las reglas por rol antes de dejar pasar la
 *       peticion hacia los microservicios.</li>
 * </ol>
 *
 * <p>Los roles viajan dentro del token, asi que no hace falta consultar ninguna
 * base de datos en cada peticion.</p>
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties({JwtProperties.class, MonitoringProperties.class})
public class SecurityConfig {

    /**
     * Rutas de la documentacion interactiva de cada servicio. Se permiten sin
     * sesion porque la documentacion ES la interfaz del proyecto: tiene que
     * cargarse antes de que exista cualquier cookie.
     *
     * <p>El gateway reenvia la ruta sin recortarle el prefijo, asi que estos
     * patrones usan exactamente el mismo nombre publico que despues reconoce
     * cada microservicio. Si se anade un microservicio nuevo, el comodin de
     * {@code /api/} lo cubre sin tocar estas reglas.</p>
     */
    private static final String[] DOCUMENTACION = {
            "/auth/swagger-ui.html", "/auth/swagger-ui/**", "/auth/v3/api-docs/**",
            "/auth/docs/**", "/auth/favicon.ico",
            "/api/*/swagger-ui.html", "/api/*/swagger-ui/**", "/api/*/v3/api-docs/**",
            "/api/*/docs/**", "/api/*/favicon.ico"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtAuthenticationConverter jwtAuthenticationConverter,
            @Qualifier("monitoringAuthenticationManager") ReactiveAuthenticationManager monitoringAuthenticationManager) {
        return http
                // API stateless: sin sesion, el token viaja en la peticion.
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                // /actuator/** (salvo health e info) se lee con HTTP Basic usando la
                // cuenta de monitoreo de Spring Boot Admin, con SU PROPIO
                // authenticationManager: asi esa cuenta de servicio nunca puede
                // autenticarse en /auth/login ni obtener un token de usuario.
                .httpBasic(basic -> basic.authenticationManager(monitoringAuthenticationManager))
                .authorizeExchange(authorize -> authorize
                        // El preflight CORS no lleva token: se responde antes de autorizar.
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // El servicio auth se encarga de decidir quien entra.
                        .pathMatchers("/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                        // La documentacion de cada servicio se publica SIN sesion a
                        // proposito. Es la unica interfaz del proyecto, asi que tiene
                        // que poder abrirse antes de iniciar sesion; si no, no habria
                        // forma de usar el boton "Try it out" de Swagger para hacer el
                        // propio login. El prefijo de cada ruta (auth, api/alumnos,
                        // api/administracion) se lo pone el gateway al reenviar.
                        .pathMatchers(DOCUMENTACION).permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .pathMatchers("/actuator/**").hasRole("ADMIN")
                        // Reglas por rol: cualquier usuario autenticado lee,
                        // solo ADMIN escribe.
                        .pathMatchers(HttpMethod.POST, "/api/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        .pathMatchers("/api/**").authenticated()
                        .pathMatchers("/auth/**").authenticated()
                        // Spring reenvia a /error cuando algo falla. Sin esta
                        // excepcion, un 404 terminaria en 403 "sin permisos".
                        .pathMatchers("/error").permitAll()
                        // Deny by default: lo que no este declarado arriba se rechaza.
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((exchange, ex) ->
                                escribirJson(exchange, HttpStatus.UNAUTHORIZED)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ex) ->
                                escribirJson(exchange, HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((exchange, ex) ->
                                escribirJson(exchange, HttpStatus.FORBIDDEN)))
                .build();
    }

    /**
     * El token llega en la cookie HttpOnly (navegador) o en la cabecera
     * Authorization (clientes de API). Este filtro traduca la cookie a cabecera
     * antes de que la cadena de seguridad la examine.
     */
    @Bean
    public WebFilter cookieTokenWebFilter(JwtProperties properties) {
        return new CookieTokenWebFilter(properties.getCookieName());
    }

    /** Convierte el claim "roles" del token (ADMIN) en autoridad ROLE_ADMIN. */
    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new ReactiveJwtGrantedAuthoritiesConverterAdapter(rolesConverter));
        return converter;
    }

    /**
     * Valida el access token con el secreto que compartio el servicio auth a
     * traves del Config Server. Comprueba firma, emisor y que sea de tipo acceso.
     *
     * <p>En WebFlux el decoder debe ser REACTIVO; el JwtDecoder de la API servlet
     * no sirve aqui.</p>
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey key = new SecretKeySpec(
                properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
                AccessTokenTypeValidator.soloAccessTokens()));
        return decoder;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // DelegatingPasswordEncoder: lee el prefijo {bcrypt} de cada contrasena.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Cuenta de servicio SOLO para /actuator/** (Spring Boot Admin). Al vivir en
     * un authenticationManager aparte del de los usuarios, no sirve para iniciar
     * sesion en el servicio auth.
     */
    @Bean
    public ReactiveAuthenticationManager monitoringAuthenticationManager(MonitoringProperties properties) {
        MonitoringProperties.MonitoringInfo monitoring = properties.getMonitoring();
        UserDetails usuario = User.withUsername(monitoring.getUsername())
                .password(monitoring.getPassword())
                .roles(monitoring.getRoles().toArray(new String[0]))
                .build();
        return new UserDetailsRepositoryReactiveAuthenticationManager(
                new MapReactiveUserDetailsService(usuario));
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

    /** Respuestas de error en JSON, en lugar del 401/403 sin cuerpo de Spring. */
    private Mono<Void> escribirJson(ServerWebExchange exchange, HttpStatus status) {
        String mensaje = status == HttpStatus.UNAUTHORIZED
                ? "Token ausente, invalido o expirado. Inicie sesion otra vez."
                : "No tiene permisos para esta operacion.";
        byte[] cuerpo = ("{\"status\":" + status.value() + ",\"error\":\""
                + status.getReasonPhrase() + "\",\"message\":\"" + mensaje + "\"}")
                .getBytes(StandardCharsets.UTF_8);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(cuerpo)));
    }
}
