package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class FechaBloqueadaResponseDTO {

    private Long id;
    private Long profesionalId;
    private String nombreProfesional;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String motivo;
    private boolean aplicaATodoElTenant;
    private LocalDateTime createdAt;
}
