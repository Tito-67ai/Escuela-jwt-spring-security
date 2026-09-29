package com.example.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuracion propia de este servicio (app.auth.*).
 *
 * <p>A diferencia de {@link JwtProperties}, esto NO se comparte: se declara en
 * {@code config/auth.yml}, que el Config Server solo sirve a la aplicacion
 * llamada "auth". Las credenciales de los usuarios quedan asi confinadas en el
 * unico servicio que las necesita, en lugar de estar replicadas (y visibles) en
 * todos los microservicios.</p>
 */
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private Map<String, UserInfo> users = new LinkedHashMap<>();
    private Cookie cookie = new Cookie();

    public Map<String, UserInfo> getUsers() {
        return users;
    }

    public void setUsers(Map<String, UserInfo> users) {
        this.users = users;
    }

    public Cookie getCookie() {
        return cookie;
    }

    public void setCookie(Cookie cookie) {
        this.cookie = cookie;
    }

    /**
     * Un usuario que puede iniciar sesion. La contrasena llega hasheada con BCrypt
     * y con el prefijo {@code {bcrypt}}, que es lo que espera el
     * DelegatingPasswordEncoder.
     */
    public static class UserInfo {
        private String password;
        private List<String> roles = new ArrayList<>();
        private boolean enabled = true;

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Cookies donde se entrega el token al navegador.
     *
     * <p>El nombre de la cookie del access token es compartido
     * ({@code app.jwt.cookie-name}) porque todos los servicios deben poder leerla;
     * el del refresh token vive aqui porque solo este servicio lo necesita.</p>
     */
    public static class Cookie {
        /**
         * Cookie del refresh token. Path=/auth para que solo salga hacia este
         * servicio: asi nunca llega a los endpoints de negocio.
         */
        private String refreshName = "refresh_token";

        /**
         * Marcar Secure. En local (http://localhost) debe quedar en false, porque
         * un navegador ignora las cookies Secure que llegan por http plano.
         */
        private boolean secure = false;

        /** Politica de cookies de terceros. Lax basta porque es el mismo sitio. */
        private String sameSite = "Lax";

        /**
         * Dominio de la cookie. Vacio = solo el host que la creo. Como todos los
         * servicios se publican en localhost, una cookie de host las alcanza a
         * todas, sin importar el puerto.
         */
        private String domain = "";

        public String getRefreshName() {
            return refreshName;
        }

        public void setRefreshName(String refreshName) {
            this.refreshName = refreshName;
        }

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }
    }
}
