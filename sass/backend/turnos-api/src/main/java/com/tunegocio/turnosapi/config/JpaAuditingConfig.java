package com.tunegocio.turnosapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Activa Spring Data JPA Auditing.
 *
 * <p>Esto hace que los campos anotados con {@code @CreatedDate} y
 * {@code @LastModifiedDate} en {@link com.tunegocio.turnosapi.entity.BaseEntity}
 * se rellenen automáticamente al persistir/actualizar entidades.
 *
 * <p>Se separa en su propia clase de configuración para evitar conflictos
 * con los contextos de test de Spring Security.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
