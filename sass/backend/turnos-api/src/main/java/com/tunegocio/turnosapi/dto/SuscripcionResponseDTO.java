package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class SuscripcionResponseDTO {

    private Long id;
    private Long tenantId;
    private String tenantNombre;
    private String tenantSlug;
    private String plan;
    private String estado;
    private LocalDate fechaInicio;
    private LocalDate fechaVencimiento;
    private String stripeSubscriptionId;
    private String mercadopagoSubscriptionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
