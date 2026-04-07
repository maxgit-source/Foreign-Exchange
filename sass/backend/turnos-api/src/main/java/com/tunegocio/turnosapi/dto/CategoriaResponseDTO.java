package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CategoriaResponseDTO {

    private Long id;
    private String nombre;
    private String descripcion;
    private Integer orden;
    private boolean activo;
    private LocalDateTime createdAt;
}
