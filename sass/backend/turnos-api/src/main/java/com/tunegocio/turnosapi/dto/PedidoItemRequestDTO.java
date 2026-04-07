package com.tunegocio.turnosapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PedidoItemRequestDTO {

    private Long productoId;

    private Long turnoId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad minima es 1")
    private Integer cantidad = 1;

    @AssertTrue(message = "Debe indicar exactamente un productoId o un turnoId por item")
    @JsonIgnore
    public boolean isOrigenValido() {
        return (productoId != null) ^ (turnoId != null);
    }
}
