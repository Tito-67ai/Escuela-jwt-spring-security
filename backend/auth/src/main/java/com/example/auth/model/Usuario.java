package com.example.auth.model;

import com.example.auth.config.AuthProperties;

import java.util.List;

/**
 * Usuario del sistema con su credencial y sus roles.
 *
 * <p>La contrasena viaja hasheada ({@code {bcrypt}...}) y nunca se compara ni se
 * devuelve: la compara BCrypt a traves del DelegatingPasswordEncoder.</p>
 */
public record Usuario(String username, String password, List<String> roles, boolean enabled) {

    /** Construye un usuario a partir de su definicion en app.auth.users. */
    public static Usuario desde(AuthProperties.UserInfo info) {
        return new Usuario(null, info.getPassword(), List.copyOf(info.getRoles()), info.isEnabled());
    }

    public Usuario conUsername(String nombre) {
        return new Usuario(nombre, password, roles, enabled);
    }

    public boolean esAdmin() {
        return roles.contains("ADMIN");
    }
}
