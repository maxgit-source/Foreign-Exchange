package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class AdminTenantResponseDTO {

    private Long tenantId;
    private String nombre;
    private String slug;
    private String email;
    private String telefono;
    private boolean activo;
    private String plan;
    private String suscripcionEstado;
    private LocalDate fechaInicio;
    private LocalDate fechaVencimiento;
}
