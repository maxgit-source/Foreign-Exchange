package com.tunegocio.turnosapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Getter
@Setter
public class TurnoRequestDTO {

    @NotNull(message = "El cliente es obligatorio")
    private Long clienteId;

    @NotNull(message = "El profesional es obligatorio")
    private Long profesionalId;

    private Long servicioId;

    private List<Long> servicioIds;

    @NotNull(message = "La fecha y hora de inicio son obligatorias")
    private LocalDateTime fechaHoraInicio;

    private Boolean recurrente = false;

    private Integer semanas;

    /** Notas opcionales sobre el turno (alergias, preferencias, etc.). */
    private String notas;

    @JsonIgnore
    public List<Long> resolveServicioIds() {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (servicioIds != null) {
            servicioIds.stream()
                    .filter(id -> id != null && id > 0)
                    .forEach(ids::add);
        }
        if (servicioId != null && servicioId > 0) {
            ids.add(servicioId);
        }
        return new ArrayList<>(ids);
    }

    @AssertTrue(message = "Debe seleccionar al menos un servicio")
    @JsonIgnore
    public boolean isServicioSelectionValid() {
        return !resolveServicioIds().isEmpty();
    }

    @AssertTrue(message = "Si el turno es recurrente debe indicar entre 1 y 52 semanas")
    @JsonIgnore
    public boolean isRecurrenciaValida() {
        return !Boolean.TRUE.equals(recurrente)
                || (semanas != null && semanas >= 1 && semanas <= 52);
    }
}
