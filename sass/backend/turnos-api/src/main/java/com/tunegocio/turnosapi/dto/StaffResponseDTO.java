package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class StaffResponseDTO {

    private Long   id;
    private String nombre;
    private String email;
    private String role;
    private String fotoUrl;
    private boolean enabled;
    private LocalDateTime createdAt;
}
