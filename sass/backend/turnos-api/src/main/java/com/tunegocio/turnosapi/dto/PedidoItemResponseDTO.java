package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PedidoItemResponseDTO {

    private Long id;
    private Long productoId;
    private Long turnoId;
    private String nombre;
    private BigDecimal precioUnitario;
    private Integer cantidad;
    private BigDecimal subtotal;
}
