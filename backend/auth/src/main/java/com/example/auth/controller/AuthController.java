package com.example.auth.controller;

import com.example.auth.config.AuthProperties;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.RefreshRequest;
import com.example.auth.dto.TokenResponse;
import com.example.auth.dto.UsuarioResponse;
import com.example.auth.model.TokensEmitidos;
import com.example.auth.service.AuthService;
import com.example.auth.service.TokenCookieManager;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de autenticacion. Todo lo que tiene que ver con credenciales y tokens
 * vive aqui, y no en el gateway ni en el frontend.
 *
 * <ul>
 *   <li>POST /auth/login   -> valida credenciales y abre sesion</li>
 *   <li>POST /auth/refresh -> renueva el access token</li>
 *   <li>POST /auth/logout  -> cierra la sesion y borra las cookies</li>
 *   <li>GET  /auth/me      -> quien esta autenticado ahora mismo</li>
 * </ul>
 *
 * <p>El token se entrega siempre en cookies HttpOnly y nunca en el cuerpo de la
 * respuesta. Asi el navegador queda autenticado sin que ninguna pagina tenga que
 * guardar el token en JavaScript.</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenCookieManager cookies;

    public AuthController(AuthService authService, TokenCookieManager cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return conCookies(authService.login(request.username(), request.password()));
    }

    /**
     * El refresh token se acepta desde la cookie (navegador) o desde el cuerpo
     * (clientes de API como curl o Postman). Si vienen los dos, manda la cookie.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @RequestBody(required = false) RefreshRequest request,
            @CookieValue(name = "${app.auth.cookie.refresh-name:refresh_token}", required = false)
            String cookieRefresh) {

        String refreshToken = cookieRefresh != null ? cookieRefresh
                : (request == null ? null : request.refreshToken());

        return conCookies(authService.refrescar(refreshToken));
    }

    /** Cierra sesion: revoca el refresh token en el servidor y borra las cookies. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        if (jwt != null) {
            authService.logout(jwt.getSubject());
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.borrarAccess().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.borrarRefresh().toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(new UsuarioResponse(true, jwt.getSubject(),
                jwt.getClaimAsStringList("roles")));
    }

    // ------------------------------------------------------------- internos

    /** Empaqueta el par de tokens en cookies HttpOnly y responde sin el token. */
    private ResponseEntity<TokenResponse> conCookies(TokensEmitidos tokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, crearAccess(tokens).toString());
        headers.add(HttpHeaders.SET_COOKIE, crearRefresh(tokens).toString());
        return ResponseEntity.ok()
                .headers(headers)
                .body(new TokenResponse(tokens.username(), tokens.roles(), tokens.expiresIn()));
    }

    private ResponseCookie crearAccess(TokensEmitidos tokens) {
        return cookies.crearAccess(tokens.accessToken(), cookies.duracionAccess());
    }

    private ResponseCookie crearRefresh(TokensEmitidos tokens) {
        return cookies.crearRefresh(tokens.refreshToken(), cookies.duracionRefresh());
    }
}
