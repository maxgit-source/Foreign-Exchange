package com.tunegocio.turnosapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cliente del negocio actual. Su aislamiento depende del schema activo.
 */
@Entity
@Table(
        name = "clientes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cliente_email", columnNames = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Cliente extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 100)
    private String apellido;

    @Column(length = 255)
    private String email;

    @Column(length = 30)
    private String telefono;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(nullable = false)
    private boolean activo = true;

    @Transient
    public String getNombreCompleto() {
        if (apellido == null || apellido.isBlank()) {
            return nombre;
        }
        return nombre + " " + apellido;
    }
}
