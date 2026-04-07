package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.exception.GlobalExceptionHandler;
import com.tunegocio.turnosapi.exception.UnauthorizedException;
import com.tunegocio.turnosapi.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private PedidoService pedidoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WebhookController controller = new WebhookController(pedidoService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mercadoPago_debeDelegarEnServicioYResponder200() throws Exception {
        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-signature", "v1=firma")
                        .content("{\"data\":{\"id\":\"123\"}}"))
                .andExpect(status().isOk());

        verify(pedidoService).procesarWebhookMercadoPago("{\"data\":{\"id\":\"123\"}}", "v1=firma");
    }

    @Test
    void stripe_debeDelegarEnServicioYResponder200() throws Exception {
        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=firma")
                        .content("{\"type\":\"payment_intent.succeeded\"}"))
                .andExpect(status().isOk());

        verify(pedidoService).procesarWebhookStripe("{\"type\":\"payment_intent.succeeded\"}", "t=1,v1=firma");
    }

    @Test
    void stripe_debeResponder401CuandoLaFirmaEsInvalida() throws Exception {
        doThrow(new UnauthorizedException("Firma de webhook de Stripe invalida"))
                .when(pedidoService).procesarWebhookStripe("{\"payload\":true}", "t=1,v1=firma");

        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=firma")
                        .content("{\"payload\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Firma de webhook de Stripe invalida"));
    }
}
