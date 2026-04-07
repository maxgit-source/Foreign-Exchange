package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PedidoResponseDTO {

    private Long id;
    private Long clienteId;
    private String nombreCliente;
    private String emailCliente;
    private String telefonoCliente;
    private String estado;
    private BigDecimal subtotal;
    private BigDecimal descuento;
    private BigDecimal costoEnvio;
    private BigDecimal total;
    private String direccionEnvio;
    private String notas;
    private String paymentIntentId;
    private String paymentMethod;
    private Instant paidAt;
    private List<PedidoItemResponseDTO> items;
    private LocalDateTime createdAt;
}
