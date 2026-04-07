package com.tunegocio.turnosapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClienteRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String nombre;

    @Size(max = 100, message = "El apellido no puede superar 100 caracteres")
    private String apellido;

    @Email(message = "El email no tiene un formato válido")
    @Size(max = 255)
    private String email;

    @Size(max = 30, message = "El teléfono no puede superar 30 caracteres")
    private String telefono;

    private String notas;
}
