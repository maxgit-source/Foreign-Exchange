package com.tunegocio.turnosapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicación TuNegocio API.
 *
 * <p>Módulos habilitados via configuración:
 * <ul>
 *   <li>{@code @EnableJpaAuditing} — en {@link com.tunegocio.turnosapi.config.JpaAuditingConfig}</li>
 *   <li>{@code @EnableAsync}       — en {@link com.tunegocio.turnosapi.config.ApplicationConfig}</li>
 *   <li>{@code @EnableMethodSecurity} — en {@link com.tunegocio.turnosapi.config.SecurityConfig}</li>
 * </ul>
 */
@SpringBootApplication
public class TurnosApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TurnosApiApplication.class, args);
    }
}
