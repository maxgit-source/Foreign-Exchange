package com.tunegocio.turnosapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(
        name = "productos",
        indexes = {
                @Index(name = "idx_productos_activo", columnList = "activo, nombre"),
                @Index(name = "idx_productos_categoria", columnList = "categoria_id"),
                @Index(name = "idx_productos_precio", columnList = "precio")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Producto extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private CategoriaProducto categoria;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(name = "precio_oferta", precision = 10, scale = 2)
    private BigDecimal precioOferta;

    @Column(nullable = false)
    private Integer stock = 0;

    @Column(length = 100)
    private String sku;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private String[] imagenes = new String[0];

    @Column(nullable = false)
    private boolean activo = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoProducto tipo = TipoProducto.FISICO;

    @Column(name = "peso_kg", precision = 8, scale = 3)
    private BigDecimal pesoKg;

    public BigDecimal getPrecioVigente() {
        return precioOferta != null ? precioOferta : precio;
    }

    public boolean hasStockLimitado() {
        return stock != null && stock >= 0;
    }
}
