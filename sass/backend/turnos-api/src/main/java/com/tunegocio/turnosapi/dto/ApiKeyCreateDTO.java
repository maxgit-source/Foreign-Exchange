package com.tunegocio.turnosapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ApiKeyCreateDTO(
        @NotBlank @Size(max = 100)
        String nombre,

        List<String> permisos
) {}
