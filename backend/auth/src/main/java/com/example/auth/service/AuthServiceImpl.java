package com.example.auth.service;

import com.example.auth.config.JwtProperties;
import com.example.auth.model.TokensEmitidos;
import com.example.auth.model.Usuario;
import com.example.auth.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokens;
    private final JwtProperties jwtProperties;

    public AuthServiceImpl(UsuarioRepository usuarios,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           RefreshTokenStore refreshTokens,
                           JwtProperties jwtProperties) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public TokensEmitidos login(String username, String rawPassword) {
        Usuario usuario = usuarios.buscarPorUsername(username)
                .orElseThrow(() -> new CredencialesInvalidasException("Usuario o contrasena incorrectos"));

        if (!usuario.enabled()) {
            throw new CredencialesInvalidasException("El usuario esta deshabilitado");
        }
        if (!passwordEncoder.matches(rawPassword, usuario.password())) {
            throw new CredencialesInvalidasException("Usuario o contrasena incorrectos");
        }
        return emitir(usuario);
    }

    @Override
    public TokensEmitidos refrescar(String refreshToken) {
        // 1) Firma, emisor y caducidad correctos.
        String username = jwtService.subjectDelRefreshToken(refreshToken);

        // 2) El usuario sigue existiendo y habilitado: si se dio de baja mientras
        //    la sesion estaba abierta, la renovacion falla.
        Usuario usuario = usuarios.buscarPorUsername(username)
                .orElseThrow(() -> new RefreshTokenInvalidoException("El usuario ya no existe"));

        // 3) El token es el vigente (rotacion y deteccion de replay).
        refreshTokens.exigirVigente(username, refreshToken);

        return emitir(usuario);
    }

    @Override
    public void logout(String username) {
        refreshTokens.revocar(username);
    }

    /** Firma el par de tokens y deja el refresh token como el unico vigente. */
    private TokensEmitidos emitir(Usuario usuario) {
        String accessToken = jwtService.generarAccessToken(usuario.username(), usuario.roles());
        String refreshToken = jwtService.generarRefreshToken(usuario.username());

        Instant expiraRefresh = Instant.now().plusMillis(jwtProperties.getRefreshExpiration());
        refreshTokens.registrar(usuario.username(), refreshToken, expiraRefresh);

        return new TokensEmitidos(usuario.username(), usuario.roles(), accessToken, refreshToken,
                jwtService.getExpirationSegundos());
    }
}
