package com.tunegocio.turnosapi.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 200)
    private String nombre;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato válido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    @NotBlank(message = "El nombre del negocio es obligatorio")
    @Size(max = 200, message = "El nombre del negocio no puede superar 200 caracteres")
    private String tenantNombre;

    @NotBlank(message = "El email del negocio es obligatorio")
    @Email(message = "El email del negocio no tiene un formato válido")
    private String tenantEmail;
}
