package com.tunegocio.turnosapi.dto;

import java.time.LocalDateTime;
import java.util.List;

public record WebhookResponseDTO(
        Long          id,
        String        url,
        List<String>  eventos,
        boolean       activo,
        LocalDateTime createdAt
) {}
