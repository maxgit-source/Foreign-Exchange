package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Información pública de un profesional para el widget de booking.
 * Solo expone los datos necesarios para que el cliente elija profesional.
 */
@Getter
@Builder
public class ProfesionalPublicoDTO {

    private Long   id;
    private String nombre;
    private String fotoUrl;
}
