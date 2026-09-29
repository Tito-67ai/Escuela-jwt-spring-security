package com.example.auth.repository;

import com.example.auth.config.AuthProperties;
import com.example.auth.model.Usuario;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Los usuarios salen de la configuracion (app.auth.users en config/auth.yml).
 *
 * <p>Se construyen una vez al arrancar y se exponen como mapa de solo lectura,
 * de forma equivalente a lo que hace un repositorio JPA pero sin base de datos:
 * las credenciales de la demo no necesitan persistir.</p>
 */
@Repository
public class UsuarioEnMemoriaRepository implements UsuarioRepository {

    private final Map<String, Usuario> usuarios;

    public UsuarioEnMemoriaRepository(AuthProperties properties) {
        Map<String, Usuario> mapa = new LinkedHashMap<>();
        properties.getUsers().forEach((username, info) ->
                mapa.put(username, Usuario.desde(info).conUsername(username)));
        this.usuarios = Map.copyOf(mapa);
    }

    @Override
    public Optional<Usuario> buscarPorUsername(String username) {
        return username == null ? Optional.empty() : Optional.ofNullable(usuarios.get(username));
    }
}
