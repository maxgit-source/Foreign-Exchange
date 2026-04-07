package com.tunegocio.turnosapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunegocio.turnosapi.dto.WebhookRequestDTO;
import com.tunegocio.turnosapi.dto.WebhookResponseDTO;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Webhook;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.WebhookRepository;
import com.tunegocio.turnosapi.util.Sha256Hasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDispatcher {

    private static final int    TIMEOUT_SEGUNDOS = 10;
    private static final String HEADER_FIRMA     = "X-TuNegocio-Signature";

    private final WebhookRepository webhookRepository;
    private final ObjectMapper      objectMapper;

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional
    public WebhookResponseDTO registrar(WebhookRequestDTO dto, Tenant tenant) {
        Webhook webhook = new Webhook();
        webhook.setTenant(tenant);
        webhook.setUrl(dto.url());
        webhook.setEventos(dto.eventos());
        Webhook saved = webhookRepository.save(webhook);
        log.info("Webhook registrado: id={}, url={}, tenant={}", saved.getId(), saved.getUrl(), tenant.getSlug());
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<WebhookResponseDTO> listar(Tenant tenant) {
        return webhookRepository.findByTenant_IdOrderByCreatedAtDesc(tenant.getId())
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public void eliminar(Long id, Tenant tenant) {
        Webhook webhook = webhookRepository.findByIdAndTenant_Id(id, tenant.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Webhook", id));
        webhookRepository.delete(webhook);
        log.info("Webhook eliminado: id={}, tenant={}", id, tenant.getSlug());
    }

    // ─── Dispatch ─────────────────────────────────────────────────────────────

    /**
     * Publica un evento a todos los webhooks activos del tenant suscritos a ese evento.
     */
    @Async
    public void publicar(Tenant tenant, String evento, Object payload) {
        List<Webhook> webhooks = webhookRepository
                .findByTenant_IdAndActivoTrueOrderByCreatedAtDesc(tenant.getId())
                .stream()
                .filter(w -> w.getEventos().contains(evento))
                .toList();

        if (webhooks.isEmpty()) return;

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(Map.of(
                    "evento",    evento,
                    "tenant",    tenant.getSlug(),
                    "timestamp", java.time.Instant.now().toString(),
                    "data",      payload
            ));
        } catch (Exception e) {
            log.error("Error serializando payload de webhook: evento={}", evento, e);
            return;
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SEGUNDOS))
                .build();

        for (Webhook webhook : webhooks) {
            enviar(client, webhook, payloadJson);
        }
    }

    private void enviar(HttpClient client, Webhook webhook, String payload) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(webhook.getUrl()))
                    .timeout(Duration.ofSeconds(TIMEOUT_SEGUNDOS))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));

            // Firma HMAC si hay secret
            if (webhook.getSecretHash() != null) {
                String firma = Sha256Hasher.hash(payload);
                builder.header(HEADER_FIRMA, "sha256=" + firma);
            }

            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Webhook entregado: id={}, status={}", webhook.getId(), response.statusCode());
            } else {
                log.warn("Webhook rechazado: id={}, status={}", webhook.getId(), response.statusCode());
            }
        } catch (Exception e) {
            log.error("Error enviando webhook id={}, url={}: {}", webhook.getId(), webhook.getUrl(), e.getMessage());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private WebhookResponseDTO toDTO(Webhook w) {
        return new WebhookResponseDTO(w.getId(), w.getUrl(), w.getEventos(), w.isActivo(), w.getCreatedAt());
    }
}
