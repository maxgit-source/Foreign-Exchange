package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.ApiKeyCreateDTO;
import com.tunegocio.turnosapi.dto.ApiKeyCreatedResponseDTO;
import com.tunegocio.turnosapi.dto.ApiKeyResponseDTO;
import com.tunegocio.turnosapi.entity.ApiKey;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.ApiKeyRepository;
import com.tunegocio.turnosapi.util.Sha256Hasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String PREFIX = "tnk_";

    private final ApiKeyRepository apiKeyRepository;

    // ─── Crear ────────────────────────────────────────────────────────────────

    @Transactional
    public ApiKeyCreatedResponseDTO crear(ApiKeyCreateDTO dto, Tenant tenant) {
        String rawKey  = generarKey();
        String keyHash = Sha256Hasher.hash(rawKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setTenant(tenant);
        apiKey.setNombre(dto.nombre());
        apiKey.setKeyHash(keyHash);
        apiKey.setPermisos(dto.permisos() != null ? dto.permisos() : List.of());
        apiKey.setActivo(true);

        ApiKey saved = apiKeyRepository.save(apiKey);
        log.info("API key creada: id={}, tenant={}", saved.getId(), tenant.getSlug());

        return new ApiKeyCreatedResponseDTO(
                saved.getId(),
                saved.getNombre(),
                rawKey,                         // valor real — mostrar solo esta vez
                saved.getPermisos(),
                saved.getCreatedAt()
        );
    }

    // ─── Listar ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ApiKeyResponseDTO> listar(Tenant tenant) {
        return apiKeyRepository.findByTenant_IdOrderByCreatedAtDesc(tenant.getId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // ─── Revocar ──────────────────────────────────────────────────────────────

    @Transactional
    public void revocar(Long id, Tenant tenant) {
        ApiKey apiKey = apiKeyRepository.findByIdAndTenant_Id(id, tenant.getId())
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id));
        apiKey.setActivo(false);
        apiKeyRepository.save(apiKey);
        log.info("API key revocada: id={}, tenant={}", id, tenant.getSlug());
    }

    // ─── Validar y actualizar uso ─────────────────────────────────────────────

    @Transactional
    public ApiKey validarYRegistrarUso(String rawKey) {
        String keyHash = Sha256Hasher.hash(rawKey);
        ApiKey apiKey  = apiKeyRepository.findByKeyHashAndActivoTrue(keyHash)
                .orElse(null);

        if (apiKey != null) {
            apiKey.setUltimoUso(LocalDateTime.now());
            apiKeyRepository.save(apiKey);
        }

        return apiKey;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String generarKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ApiKeyResponseDTO toDTO(ApiKey apiKey) {
        return new ApiKeyResponseDTO(
                apiKey.getId(),
                apiKey.getNombre(),
                buildPreview(apiKey.getKeyHash()),
                apiKey.getPermisos(),
                apiKey.isActivo(),
                apiKey.getUltimoUso(),
                apiKey.getCreatedAt()
        );
    }

    private String buildPreview(String keyHash) {
        // Muestra solo los primeros 8 chars del hash como identificador visual
        return keyHash.substring(0, Math.min(8, keyHash.length())) + "...";
    }
}
