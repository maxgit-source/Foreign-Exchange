package com.tunegocio.turnosapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductoStockUpdateDTO {

    @NotNull(message = "El stock es obligatorio")
    @Min(value = -1, message = "El stock minimo permitido es -1")
    private Integer stock;
}
