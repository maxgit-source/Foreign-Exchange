package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Webhooks", description = "Confirmaciones de pago de proveedores externos")
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PedidoService pedidoService;

    @Operation(summary = "Webhook de Mercado Pago")
    @PostMapping("/mercadopago")
    public ResponseEntity<Void> mercadoPago(
            @RequestBody String payload,
            @RequestHeader(value = "x-signature", required = false) String signatureHeader) {
        pedidoService.procesarWebhookMercadoPago(payload, signatureHeader);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Webhook de Stripe")
    @PostMapping("/stripe")
    public ResponseEntity<Void> stripe(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader) {
        pedidoService.procesarWebhookStripe(payload, signatureHeader);
        return ResponseEntity.ok().build();
    }
}
