package com.tunegocio.turnosapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunegocio.turnosapi.dto.SlotDisponibleDTO;
import com.tunegocio.turnosapi.dto.SlotRecomendadoDTO;
import com.tunegocio.turnosapi.entity.Turno;
import com.tunegocio.turnosapi.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Servicio de IA para recomendación de slots usando Claude API.
 * Se activa solo si {@code ai.enabled=true} en application.yml.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AISchedulingService {

    private static final String CLAUDE_API_URL     = "https://api.anthropic.com/v1/messages";
    private static final String CLAUDE_MODEL        = "claude-haiku-4-5-20251001";
    private static final int    MAX_TOKENS          = 512;
    private static final int    HISTORIAL_LIMIT     = 20;

    @Value("${anthropic.api-key}")
    private String apiKey;

    private final TurnoRepository    turnoRepository;
    private final ObjectMapper       objectMapper;

    /**
     * Recomienda los mejores slots para un cliente basándose en su historial.
     * @param clienteId     ID del cliente
     * @param slotsDisponibles slots disponibles calculados por DisponibilidadService
     * @param fecha         fecha objetivo
     * @return lista de slots rankeados con razón
     */
    public List<SlotRecomendadoDTO> recomendarSlots(Long clienteId,
                                                     List<SlotDisponibleDTO> slotsDisponibles,
                                                     LocalDate fecha) {
        if (slotsDisponibles.isEmpty()) return List.of();

        List<Turno> historial = turnoRepository
                .findByCliente_IdOrderByFechaHoraInicioDesc(clienteId)
                .stream()
                .limit(HISTORIAL_LIMIT)
                .toList();

        String prompt = buildPrompt(historial, slotsDisponibles, fecha);

        try {
            String respuesta = callClaude(prompt);
            return parsearRecomendaciones(respuesta, slotsDisponibles, fecha);
        } catch (Exception e) {
            log.warn("Error llamando a Claude API para scheduling: {}", e.getMessage());
            // Fallback: devolver slots sin ordenar
            return slotsDisponibles.stream()
                    .map(s -> new SlotRecomendadoDTO(fecha, s.getHoraInicio(), s.getHoraFin(), "Slot disponible", 0.5))
                    .toList();
        }
    }

    private String buildPrompt(List<Turno> historial, List<SlotDisponibleDTO> slots, LocalDate fecha) {
        String historialStr = historial.isEmpty()
                ? "Sin historial previo"
                : historial.stream()
                        .limit(5)
                        .map(t -> "- " + t.getFechaHoraInicio().toString())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");

        String slotsStr = slots.stream()
                .map(s -> s.getHoraInicio() + "-" + s.getHoraFin())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        return """
                Eres un asistente de scheduling para un negocio de servicios.

                Historial de turnos del cliente (más recientes primero):
                %s

                Slots disponibles para el %s:
                %s

                Basándote en los patrones del historial del cliente (hora preferida del día, día de la semana),
                recomienda los 3 mejores slots. Si no hay historial, recomienda por horario popular.

                Responde SOLO con JSON válido, sin texto adicional:
                [{"horaInicio": "HH:mm", "horaFin": "HH:mm", "razon": "texto breve", "score": 0.0}]
                """.formatted(historialStr, fecha.format(DateTimeFormatter.ISO_LOCAL_DATE), slotsStr);
    }

    private String callClaude(String prompt) throws Exception {
        Map<String, Object> body = Map.of(
                "model",      CLAUDE_MODEL,
                "max_tokens", MAX_TOKENS,
                "messages",   List.of(Map.of("role", "user", "content", prompt))
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CLAUDE_API_URL))
                .header("Content-Type",      "application/json")
                .header("x-api-key",         apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API error: " + response.statusCode());
        }

        Map<String, Object> resp = objectMapper.readValue(response.body(),
                new TypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) resp.get("content");
        return (String) content.get(0).get("text");
    }

    private List<SlotRecomendadoDTO> parsearRecomendaciones(String json,
                                                              List<SlotDisponibleDTO> disponibles,
                                                              LocalDate fecha) {
        try {
            List<Map<String, Object>> recomendaciones = objectMapper.readValue(json,
                    new TypeReference<>() {});

            return recomendaciones.stream()
                    .map(r -> {
                        String horaInicioStr = (String) r.get("horaInicio");
                        String horaFinStr    = (String) r.get("horaFin");
                        String razon         = (String) r.getOrDefault("razon", "Recomendado");
                        double score         = ((Number) r.getOrDefault("score", 0.5)).doubleValue();

                        // Verificar que el slot recomendado aún está disponible
                        return disponibles.stream()
                                .filter(s -> s.getHoraInicio().toString().startsWith(horaInicioStr))
                                .findFirst()
                                .map(s -> new SlotRecomendadoDTO(fecha, s.getHoraInicio(), s.getHoraFin(), razon, score))
                                .orElse(null);
                    })
                    .filter(s -> s != null)
                    .limit(3)
                    .toList();
        } catch (Exception e) {
            log.warn("Error parseando respuesta de Claude: {}", e.getMessage());
            return List.of();
        }
    }
}
