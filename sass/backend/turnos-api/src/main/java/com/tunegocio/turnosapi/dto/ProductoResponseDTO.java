package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProductoResponseDTO {

    private Long id;
    private Long categoriaId;
    private String categoriaNombre;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private BigDecimal precioOferta;
    private BigDecimal precioVigente;
    private Integer stock;
    private String sku;
    private List<String> imagenes;
    private String tipo;
    private BigDecimal pesoKg;
    private boolean activo;
    private LocalDateTime createdAt;
}
