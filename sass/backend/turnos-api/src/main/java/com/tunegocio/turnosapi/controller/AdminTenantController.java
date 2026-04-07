package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.AdminTenantResponseDTO;
import com.tunegocio.turnosapi.dto.CambiarPlanRequestDTO;
import com.tunegocio.turnosapi.dto.SuscripcionResponseDTO;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.service.SuscripcionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Tenants", description = "Administracion global de tenants y suscripciones")
@RestController
@RequestMapping("/api/admin/tenants")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTenantController {

    private final SuscripcionService suscripcionService;

    @Operation(summary = "Listar tenants con plan y estado")
    @GetMapping
    public ResponseEntity<Page<AdminTenantResponseDTO>> listar(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(suscripcionService.listarTenants(pageable));
    }

    @Operation(summary = "Cambiar plan del tenant")
    @PatchMapping("/{id}/plan")
    public ResponseEntity<SuscripcionResponseDTO> cambiarPlan(
            @PathVariable Long id,
            @Valid @RequestBody CambiarPlanRequestDTO dto,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(suscripcionService.cambiarPlan(id, dto, actor));
    }

    @Operation(summary = "Obtener suscripcion del tenant")
    @GetMapping("/{id}/suscripcion")
    public ResponseEntity<SuscripcionResponseDTO> obtenerSuscripcion(@PathVariable Long id) {
        return ResponseEntity.ok(suscripcionService.obtenerPorTenantId(id));
    }

}
