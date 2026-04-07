package com.tunegocio.turnosapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "categorias",
        indexes = {
                @Index(name = "idx_categoria_nombre", columnList = "nombre"),
                @Index(name = "idx_categoria_activo_orden", columnList = "activo, orden")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Categoria extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private Integer orden = 0;

    @Column(nullable = false)
    private boolean activo = true;
}
