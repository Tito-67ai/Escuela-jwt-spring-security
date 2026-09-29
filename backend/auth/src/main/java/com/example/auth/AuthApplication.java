package com.example.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Microservicio de autenticacion.
 *
 * <p>Es el UNICO servicio del sistema que:</p>
 * <ul>
 *   <li>conoce las credenciales de los usuarios,</li>
 *   <li>firma los tokens JWT,</li>
 *   <li>los entrega al navegador en cookies HttpOnly.</li>
 * </ul>
 *
 * <p>Ni el gateway ni los microservicios de negocio emiten tokens: solo los
 * validan. Asi el credencial no se duplica por el sistema y el frontend no
 * necesita guardar ningun token en JavaScript.</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
