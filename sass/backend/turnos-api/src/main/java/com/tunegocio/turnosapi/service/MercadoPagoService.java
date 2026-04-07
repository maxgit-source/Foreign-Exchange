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
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MercadoPagoService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Value("${mercadopago.access-token:}")
    private String accessToken;

    @Value("${mercadopago.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    @Value("${app.payments.success-path:/pago/exito}")
    private String successPath;

    @Value("${app.payments.failure-path:/pago/error}")
    private String failurePath;

    @Value("${app.payments.pending-path:/pago/pendiente}")
    private String pendingPath;

    public PaymentGatewayResult crearPreferencia(Pedido pedido, Tenant tenant) {
        requireAccessToken();

        List<Map<String, Object>> items = pedido.getItems().stream()
                .map(item -> Map.<String, Object>of(
                        "title", item.getNombre(),
                        "quantity", item.getCantidad(),
                        "unit_price", item.getPrecioUnitario(),
                        "currency_id", "ARS"
                ))
                .toList();

        Map<String, Object> body = Map.of(
                "items", items,
                "external_reference", buildExternalReference(tenant, pedido),
                "back_urls", Map.of(
                        "success", frontendUrl + successPath,
                        "failure", frontendUrl + failurePath,
                        "pending", frontendUrl + pendingPath
                ),
                "notification_url", backendUrl + "/api/webhooks/mercadopago",
                "auto_return", "approved"
        );

        Map<String, Object> response = restClient.post()
                .uri("https://api.mercadopago.com/checkout/preferences")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP_TYPE);

        String preferenceId = stringValue(response, "id");
        String initPoint = stringValue(response, "init_point");
        if (preferenceId == null || initPoint == null) {
            throw new IllegalStateException("Mercado Pago no devolvio una preferencia valida");
        }

        return new PaymentGatewayResult(preferenceId, initPoint, null);
    }

    public Optional<PaymentWebhookResolution> validarYResolverPago(String payload, String signatureHeader) {
        validarFirma(payload, signatureHeader);
        requireAccessToken();

        String paymentId = extractPaymentId(payload);
        if (paymentId == null) {
            return Optional.empty();
        }

        JsonNode payment = restClient.get()
                .uri("https://api.mercadopago.com/v1/payments/{id}", paymentId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);

        if (payment == null || !"approved".equalsIgnoreCase(payment.path("status").asText())) {
            return Optional.empty();
        }

        String externalReference = payment.path("external_reference").asText(null);
        if (externalReference == null || externalReference.isBlank()) {
            return Optional.empty();
        }

        return parseExternalReference(externalReference)
                .map(reference -> new PaymentWebhookResolution(
                        reference.tenantSlug(),
                        reference.pedidoId(),
                        payment.path("id").asText(paymentId)
                ));
    }

    private void validarFirma(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new UnauthorizedException("Firma de webhook de Mercado Pago ausente");
        }

        String provided = normalizeSignature(signatureHeader);
        String expected = hmacSha256Hex(payload, webhookSecret);

        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Firma de webhook de Mercado Pago invalida");
        }
    }

    private String extractPaymentId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode dataId = root.path("data").path("id");
            if (!dataId.isMissingNode() && !dataId.isNull()) {
                return dataId.asText();
            }
            JsonNode id = root.path("id");
            return id.isMissingNode() || id.isNull() ? null : id.asText();
        } catch (Exception ex) {
            throw new BusinessException("Payload de Mercado Pago invalido");
        }
    }

    private void requireAccessToken() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException("Mercado Pago no esta configurado");
        }
    }

    private String buildExternalReference(Tenant tenant, Pedido pedido) {
        return tenant.getSlug() + ":" + pedido.getId();
    }

    private Optional<ExternalReference> parseExternalReference(String externalReference) {
        String[] parts = externalReference.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ExternalReference(parts[0], Long.valueOf(parts[1])));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String normalizeSignature(String signatureHeader) {
        return List.of(signatureHeader.split("[,;]")).stream()
                .map(String::trim)
                .filter(part -> part.startsWith("v1=") || part.startsWith("sha256="))
                .map(part -> part.substring(part.indexOf('=') + 1))
                .findFirst()
                .orElse(signatureHeader.trim());
    }

    private String hmacSha256Hex(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo validar la firma de Mercado Pago", ex);
        }
    }

    private String stringValue(Map<String, Object> map, String key) {
        Object value = map != null ? map.get(key) : null;
        if (value instanceof String string && !string.isBlank()) {
            return string;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return value != null ? value.toString() : null;
    }

    private record ExternalReference(String tenantSlug, Long pedidoId) {
    }
}
