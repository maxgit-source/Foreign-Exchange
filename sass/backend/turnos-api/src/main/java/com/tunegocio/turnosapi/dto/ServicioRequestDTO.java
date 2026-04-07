package com.tunegocio.turnosapi.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ServicioRequestDTO {

    @NotBlank(message = "El nombre del servicio es obligatorio")
    @Size(max = 200, message = "El nombre no puede superar 200 caracteres")
    private String nombre;

    private String descripcion;

    @NotNull(message = "La duración es obligatoria")
    @Min(value = 5,    message = "La duración mínima es 5 minutos")
    @Max(value = 480,  message = "La duración máxima es 480 minutos (8 horas)")
    private Integer duracionMinutos;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
    @Digits(integer = 8, fraction = 2, message = "Formato de precio inválido")
    private BigDecimal precio;

    private Long categoriaId;

    private boolean activo = true;
}
