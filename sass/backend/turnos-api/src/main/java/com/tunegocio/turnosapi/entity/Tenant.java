package com.tunegocio.turnosapi.entity;

import com.tunegocio.turnosapi.config.TenantContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representa un negocio suscriptor de la plataforma.
 *
 * <p>Esta entidad vive siempre en el schema global {@code public}. El resto
 * de los datos operativos del negocio se alojan en su propio schema dedicado.
 */
@Entity
@Table(
        schema = "public",
        name = "tenants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tenant_slug", columnNames = "slug"),
                @UniqueConstraint(name = "uk_tenant_email", columnNames = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Tenant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 30)
    private String telefono;

    @Column(length = 500)
    private String direccion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.FREE;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false, length = 60)
    private String timezone = "America/Argentina/Buenos_Aires";

    @Column(length = 500)
    private String logoUrl;

    @Column(length = 7)
    private String colorPrimario = "#6366f1";

    @Transient
    public String getSchemaName() {
        return TenantContext.schemaForSlug(slug);
    }
}
