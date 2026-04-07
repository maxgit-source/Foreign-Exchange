package com.tunegocio.turnosapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlogCategoriaCreateDTO(
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Size(max = 150) String slug,
        String descripcion
) {}
