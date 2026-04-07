package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ServicioResponseDTO {

    private Long       id;
    private String     nombre;
    private String     descripcion;
    private Integer    duracionMinutos;
    private BigDecimal precio;
    private Long       categoriaId;
    private String     categoriaNombre;
    private String     imagenUrl;
    private boolean    activo;
    private LocalDateTime createdAt;
}
