package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class TurnoServicioDTO {

    private Long id;
    private String nombre;
    private Integer duracionMinutos;
    private BigDecimal precio;
    private Long categoriaId;
    private String categoriaNombre;
    private String imagenUrl;
}
