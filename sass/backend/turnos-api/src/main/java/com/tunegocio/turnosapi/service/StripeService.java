package com.tunegocio.turnosapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunegocio.turnosapi.entity.Pedido;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.exception.BusinessException;
import com.tunegocio.turnosapi.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StripeService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.payments.default-currency:ARS}")
    private String defaultCurrency;

    public PaymentGatewayResult crearPaymentIntent(Pedido pedido, Tenant tenant) {
        requireSecretKey();

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("amount", toStripeAmount(pedido.getTotal()).toString());
        body.add("currency", defaultCurrency.toLowerCase());
        body.add("metadata[pedido_id]", pedido.getId().toString());
        body.add("metadata[tenant_slug]", tenant.getSlug());
        body.add("automatic_payment_methods[enabled]", "true");

        Map<String, Object> response = restClient.post()
                .uri("https://api.stripe.com/v1/payment_intents")
                .header("Authorization", "Bearer " + secretKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(MAP_TYPE);

        String intentId = stringValue(response, "id");
        String clientSecret = stringValue(response, "client_secret");
        if (intentId == null || clientSecret == null) {
            throw new IllegalStateException("Stripe no devolvio un payment intent valido");
        }
        return new PaymentGatewayResult(intentId, null, clientSecret);
    }

    public Optional<PaymentWebhookResolution> validarYResolverPago(String payload, String signatureHeader) {
        validarFirma(payload, signatureHeader);

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new BusinessException("Payload de Stripe invalido");
        }

        if (!"payment_intent.succeeded".equals(root.path("type").asText())) {
            return Optional.empty();
        }

        JsonNode paymentIntent = root.path("data").path("object");
        String pedidoId = paymentIntent.path("metadata").path("pedido_id").asText(null);
        String tenantSlug = paymentIntent.path("metadata").path("tenant_slug").asText(null);
        if (pedidoId == null || pedidoId.isBlank() || tenantSlug == null || tenantSlug.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new PaymentWebhookResolution(tenantSlug, Long.valueOf(pedidoId), paymentIntent.path("id").asText()));
    }

    private void validarFirma(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new UnauthorizedException("Stripe no esta configurado para validar webhooks");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new UnauthorizedException("Firma de webhook de Stripe ausente");
        }

        Map<String, String> values = Arrays.stream(signatureHeader.split(","))
                .map(String::trim)
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (a, b) -> a));

        String timestamp = values.get("t");
        String signature = values.get("v1");
        if (timestamp == null || signature == null) {
            throw new UnauthorizedException("Cabecera de firma de Stripe invalida");
        }

        long epoch = Long.parseLong(timestamp);
        if (Math.abs(Instant.now().getEpochSecond() - epoch) > 300) {
            throw new UnauthorizedException("La firma de Stripe expiro");
        }

        String signedPayload = timestamp + "." + payload;
        String expected = hmacSha256Hex(signedPayload, webhookSecret);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Firma de webhook de Stripe invalida");
        }
    }

    private Long toStripeAmount(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private void requireSecretKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new BusinessException("Stripe no esta configurado");
        }
    }

    private String hmacSha256Hex(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo validar la firma de Stripe", ex);
        }
    }

    private String stringValue(Map<String, Object> map, String key) {
        Object value = map != null ? map.get(key) : null;
        return value != null ? value.toString() : null;
    }
}
