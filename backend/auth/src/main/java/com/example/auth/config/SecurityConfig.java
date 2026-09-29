package com.example.auth.config;

import com.example.auth.service.JwtService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Seguridad del servicio de autenticacion.
 *
 * <p>Este servicio es distinto de los demas en un punto: no se limita a validar
 * tokens, tambien los emite. Aun asi se protege a si mismo con las mismas reglas
 * que el resto del sistema:</p>
 * <ul>
 *   <li>{@code /auth/login} y {@code /auth/refresh} son publicos: sin sesion no
 *       hay token, asi que son la puerta de entrada.</li>
 *   <li>{@code /auth/me} y {@code /auth/logout} exigen token valido.</li>
 *   <li>El resto de rutas se niegan.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class, MonitoringProperties.class})
public class SecurityConfig {

    /**
     * Cadena 1: /actuator/**, con HTTP Basic y la cuenta de monitoreo que usa
     * Spring Boot Admin. Va en su propia cadena para no mezclarla con el JWT.
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

    /** Cadena 2: la API de autenticacion. */
    @Bean
    @Order(2)
    public SecurityFilterChain authFilterChain(HttpSecurity http,
                                                JwtDecoder jwtDecoder,
                                                JwtAuthenticationConverter jwtAuthenticationConverter,
                                                BearerTokenResolver bearerTokenResolver) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // El preflight CORS no lleva token.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/login", "/auth/refresh").permitAll()
                        .requestMatchers("/auth/logout", "/auth/me").authenticated()
                        // Documentacion interactiva de la API. Se publica sin sesion
                        // a proposito: es la unica "interfaz" del proyecto y tiene que
                        // poder consultarse antes de iniciar sesion.
                        //
                        // Van las dos formas de la ruta: la corta (/swagger-ui.html,
                        // al llamar al servicio directamente) y la que llega del
                        // gateway (/auth/swagger-ui.html), que reenvia el prefijo sin
                        // recortarlo. El CSS del tema cuelga de /docs/ para no chocar
                        // con ninguna de las rutas de /auth/**. Si faltara alguna, la
                        // documentacion alcanzaria este servicio pero el denyAll() la
                        // devolveria con un 401.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/docs/**", "/favicon.ico",
                                "/auth/v3/api-docs/**", "/auth/swagger-ui/**",
                                "/auth/swagger-ui.html", "/auth/docs/**",
                                "/auth/favicon.ico").permitAll()
                        // Spring reenvia a /error cuando algo falla; sin esta
                        // excepcion, un 404 terminaria en 403 "sin permisos".
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

    /**
     * El token se acepta en la cabecera Authorization o en la cookie
     * HttpOnly, que es lo que hace innecesario cualquier codigo de token en el
     * navegador.
     */
    @Bean
    public BearerTokenResolver bearerTokenResolver(JwtProperties properties) {
        return new CookieOrHeaderBearerTokenResolver(properties.getCookieName());
    }

    /**
     * Valida el access token con el secreto compartido del Config Server. Este
     * servicio necesita decoder porque /auth/me y /auth/logout exigen sesion.
     *
     * <p>Se exige ademas {@code typ=access}: aunque el refresh token se firma con
     * otro secreto, comprobar el tipo deja claro que /auth/me nunca acepta uno.</p>
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey key = new SecretKeySpec(
                properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
                soloAccessTokens()));
        return decoder;
    }

    /** Solo valen los tokens de tipo acceso; un refresh token no abre sesion. */
    private OAuth2TokenValidator<Jwt> soloAccessTokens() {
        return token -> {
            Object tipo = token.getClaims().get("typ");
            if (tipo != null && "access".equals(tipo.toString())) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "El token presentado no es un access token",
                    null));
        };
    }

    /** Claim "roles" (ADMIN) -> autoridad ROLE_ADMIN, que es lo que usa hasRole(...). */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName(JwtService.CLAIM_ROLES);
        rolesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(rolesConverter);
        return converter;
    }

    /** DelegatingPasswordEncoder: lee el prefijo {bcrypt} de cada contrasena. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Solo la cuenta de monitoreo, para el HTTP Basic de /actuator/**. */
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
        config.setAllowCredentials(true);
        // Con credenciales, el navegador necesita verlos explicitamente: no vale
        // con "*", o las cookies no llegan al frontend.
        config.setExposedHeaders(List.of("Authorization"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
