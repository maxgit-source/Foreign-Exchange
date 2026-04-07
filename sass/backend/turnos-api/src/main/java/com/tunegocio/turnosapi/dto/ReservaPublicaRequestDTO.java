package com.tunegocio.turnosapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * DTO para el endpoint público de booking.
 * El cliente no está autenticado: proporciona sus datos directamente.
 * Si ya existe un cliente con ese email en el tenant, se reutiliza.
 */
@Getter
@Setter
public class ReservaPublicaRequestDTO {

    @NotNull(message = "El profesional es obligatorio")
    private Long profesionalId;

    private Long servicioId;

    private List<Long> servicioIds;

    @NotNull(message = "La fecha y hora de inicio son obligatorias")
    private LocalDateTime fechaHoraInicio;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    private String nombreCliente;

    @Size(max = 100)
    private String apellidoCliente;

    @NotBlank(message = "El email es obligatorio para recibir confirmación")
    @Email(message = "El email no tiene un formato válido")
    private String emailCliente;

    @Size(max = 30)
    private String telefonoCliente;

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
}
