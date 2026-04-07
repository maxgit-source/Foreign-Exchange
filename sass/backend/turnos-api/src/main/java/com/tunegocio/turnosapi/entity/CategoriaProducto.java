package com.tunegocio.turnosapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "categorias_producto",
        indexes = {
                @Index(name = "idx_categoria_producto_activo_orden", columnList = "activo, orden, nombre")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class CategoriaProducto extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "imagen_url", length = 1000)
    private String imagenUrl;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false)
    private Integer orden = 0;
}
