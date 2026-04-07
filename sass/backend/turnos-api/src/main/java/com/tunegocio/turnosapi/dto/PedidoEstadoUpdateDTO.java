package com.tunegocio.turnosapi.dto;

import com.tunegocio.turnosapi.entity.PedidoEstado;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PedidoEstadoUpdateDTO {

    @NotNull(message = "El estado es obligatorio")
    private PedidoEstado estado;
}
