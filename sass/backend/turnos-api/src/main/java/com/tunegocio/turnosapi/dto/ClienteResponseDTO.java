package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ClienteResponseDTO {

    private Long   id;
    private String nombre;
    private String apellido;
    private String nombreCompleto;
    private String email;
    private String telefono;
    private String notas;
    private boolean activo;
    private LocalDateTime createdAt;
}
