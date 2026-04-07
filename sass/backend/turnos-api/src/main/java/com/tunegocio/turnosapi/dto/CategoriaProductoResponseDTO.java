package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CategoriaProductoResponseDTO {

    private Long id;
    private String nombre;
    private String descripcion;
    private String imagenUrl;
    private Integer orden;
    private boolean activo;
    private LocalDateTime createdAt;
}
