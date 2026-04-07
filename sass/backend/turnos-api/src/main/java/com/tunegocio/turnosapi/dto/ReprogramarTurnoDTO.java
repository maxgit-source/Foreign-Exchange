package com.tunegocio.turnosapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReprogramarTurnoDTO {

    @NotNull(message = "La nueva fecha y hora son obligatorias")
    private LocalDateTime nuevaFechaHoraInicio;
}
