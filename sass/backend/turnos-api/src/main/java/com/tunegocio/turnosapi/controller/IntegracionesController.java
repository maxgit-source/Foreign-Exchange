package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.*;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.service.ApiKeyService;
import com.tunegocio.turnosapi.service.PlanValidator;
import com.tunegocio.turnosapi.service.WebhookDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Integraciones", description = "API Keys y Webhooks para integraciones externas (plan PRO+)")
@RestController
@RequestMapping("/api/integraciones")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class IntegracionesController {

    private final ApiKeyService    apiKeyService;
    private final WebhookDispatcher webhookDispatcher;
    private final PlanValidator    planValidator;

    // ─── API Keys ─────────────────────────────────────────────────────────────

    @Operation(summary = "Generar nueva API key (plan PRO+)")
    @PostMapping("/api-keys")
    public ResponseEntity<ApiKeyCreatedResponseDTO> crearApiKey(
            @Valid @RequestBody ApiKeyCreateDTO dto,
            @AuthenticationPrincipal Usuario usuario) {

        planValidator.validarPuedeUsarApiPublica(tenant(usuario));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiKeyService.crear(dto, tenant(usuario)));
    }

    @Operation(summary = "Listar API keys del negocio")
    @GetMapping("/api-keys")
    public ResponseEntity<List<ApiKeyResponseDTO>> listarApiKeys(
            @AuthenticationPrincipal Usuario usuario) {

        planValidator.validarPuedeUsarApiPublica(tenant(usuario));
        return ResponseEntity.ok(apiKeyService.listar(tenant(usuario)));
    }

    @Operation(summary = "Revocar una API key")
    @DeleteMapping("/api-keys/{id}")
    public ResponseEntity<Void> revocarApiKey(
            @PathVariable Long id,
            @AuthenticationPrincipal Usuario usuario) {

        planValidator.validarPuedeUsarApiPublica(tenant(usuario));
        apiKeyService.revocar(id, tenant(usuario));
        return ResponseEntity.noContent().build();
    }

    // ─── Webhooks ─────────────────────────────────────────────────────────────

    @Operation(summary = "Registrar endpoint de webhook")
    @PostMapping("/webhooks")
    public ResponseEntity<WebhookResponseDTO> registrarWebhook(
            @Valid @RequestBody WebhookRequestDTO dto,
            @AuthenticationPrincipal Usuario usuario) {

        planValidator.validarPuedeUsarApiPublica(tenant(usuario));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(webhookDispatcher.registrar(dto, tenant(usuario)));
    }

    @Operation(summary = "Listar webhooks del negocio")
    @GetMapping("/webhooks")
    public ResponseEntity<List<WebhookResponseDTO>> listarWebhooks(
            @AuthenticationPrincipal Usuario usuario) {

        planValidator.validarPuedeUsarApiPublica(tenant(usuario));
        return ResponseEntity.ok(webhookDispatcher.listar(tenant(usuario)));
    }

    @Operation(summary = "Eliminar un webhook")
    @DeleteMapping("/webhooks/{id}")
    public ResponseEntity<Void> eliminarWebhook(
            @PathVariable Long id,
            @AuthenticationPrincipal Usuario usuario) {

        planValidator.validarPuedeUsarApiPublica(tenant(usuario));
        webhookDispatcher.eliminar(id, tenant(usuario));
        return ResponseEntity.noContent().build();
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private Tenant tenant(Usuario usuario) {
        return usuario.getTenant();
    }
}
