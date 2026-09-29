package com.example.auth.repository;

import com.example.auth.model.Usuario;

import java.util.Optional;

/**
 * Fuente de los usuarios que pueden iniciar sesion.
 *
 * <p>Se declara como interfaz para que el origen sea intercambiable: hoy se
 * cumple desde la configuracion ({@code app.auth.users}) y manana podria ser una
 * tabla en base de datos, sin que cambie ni el controlador ni el servicio.</p>
 */
public interface UsuarioRepository {

    Optional<Usuario> buscarPorUsername(String username);
}
