package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Respuesta de autenticación. Contiene ambos tokens y los datos
 * del usuario necesarios para inicializar la sesión en el cliente.
 */
@Getter
@Builder
public class AuthResponseDTO {

    private String accessToken;
    private String refreshToken;

    /** Tipo de token. Siempre "Bearer". */
    private String tokenType;

    /** Segundos hasta que el accessToken expira. */
    private long expiresIn;

    private Long   usuarioId;
    private String nombre;
    private String email;
    private String role;
    private Long   tenantId;
    private String tenantNombre;
    private String tenantSlug;
}
