package com.tunegocio.turnosapi.dto;

import com.tunegocio.turnosapi.entity.Plan;
import com.tunegocio.turnosapi.entity.SuscripcionEstado;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CambiarPlanRequestDTO {

    @NotNull(message = "El plan es obligatorio")
    private Plan plan;

    private SuscripcionEstado estado;

    private LocalDate fechaVencimiento;

    private String motivo;
}
