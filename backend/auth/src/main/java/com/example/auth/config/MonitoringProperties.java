package com.example.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuenta de servicio para /actuator/** (app.security.monitoring).
 *
 * <p>Vive en la configuracion compartida porque Spring Boot Admin necesita leer
 * los endpoints de actuator de TODOS los servicios con las mismas credenciales.
 * Al estar en su propia cadena de seguridad, esta cuenta no puede usarse para
 * iniciar sesion en /auth/login: obtenerla no da acceso a ningun token de
 * usuario.</p>
 */
@ConfigurationProperties(prefix = "app.security")
public class MonitoringProperties {

    private MonitoringInfo monitoring = new MonitoringInfo();

    public MonitoringInfo getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(MonitoringInfo monitoring) {
        this.monitoring = monitoring;
    }

    public static class MonitoringInfo {
        private String username;
        private String password;
        private List<String> roles = new ArrayList<>();

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

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
    }
}
