package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CheckoutResponseDTO {

    private PedidoResponseDTO pedido;
    private String paymentMethod;
    private String externalReference;
    private String checkoutUrl;
    private String clientSecret;
}
