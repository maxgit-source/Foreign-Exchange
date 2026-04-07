package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Información pública del tenant visible para clientes que reservan
 * sin autenticación. No expone datos sensibles.
 */
@Getter
@Builder
public class TenantPublicoDTO {

    private String nombre;
    private String slug;
    private String telefono;
    private String logoUrl;
    private String colorPrimario;
    private String timezone;
}
