package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.*;
import com.tunegocio.turnosapi.service.SitioBuilderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Site Builder", description = "Configuración del sitio público y Page Builder")
@RestController
@RequestMapping("/api/site-builder")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class SiteBuilderController {

    private final SitioBuilderService sitioBuilderService;

    // ─── Config del sitio ────────────────────────────────────────────────────

    @Operation(summary = "Obtener configuración del sitio")
    @GetMapping("/config")
    public ResponseEntity<SitioConfigDTO> obtenerConfig() {
        return ResponseEntity.ok(sitioBuilderService.obtenerConfig());
    }

    @Operation(summary = "Actualizar configuración del sitio")
    @PatchMapping("/config")
    public ResponseEntity<SitioConfigDTO> actualizarConfig(
            @Valid @RequestBody SitioConfigUpdateDTO dto) {
        return ResponseEntity.ok(sitioBuilderService.actualizarConfig(dto));
    }

    // ─── Páginas ─────────────────────────────────────────────────────────────

    @Operation(summary = "Listar páginas del sitio")
    @GetMapping("/paginas")
    public ResponseEntity<List<PaginaDTO>> listarPaginas() {
        return ResponseEntity.ok(sitioBuilderService.listarPaginas());
    }

    @Operation(summary = "Obtener página con sus secciones")
    @GetMapping("/paginas/{id}")
    public ResponseEntity<PaginaDTO> obtenerPagina(@PathVariable Long id) {
        return ResponseEntity.ok(sitioBuilderService.obtenerPagina(id));
    }

    @Operation(summary = "Crear nueva página")
    @PostMapping("/paginas")
    public ResponseEntity<PaginaDTO> crearPagina(@Valid @RequestBody PaginaCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sitioBuilderService.crearPagina(dto));
    }

    @Operation(summary = "Actualizar página")
    @PutMapping("/paginas/{id}")
    public ResponseEntity<PaginaDTO> actualizarPagina(
            @PathVariable Long id, @Valid @RequestBody PaginaCreateDTO dto) {
        return ResponseEntity.ok(sitioBuilderService.actualizarPagina(id, dto));
    }

    @Operation(summary = "Eliminar página")
    @DeleteMapping("/paginas/{id}")
    public ResponseEntity<Void> eliminarPagina(@PathVariable Long id) {
        sitioBuilderService.eliminarPagina(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Secciones ────────────────────────────────────────────────────────────

    @Operation(summary = "Listar secciones de una página")
    @GetMapping("/paginas/{paginaId}/secciones")
    public ResponseEntity<List<SeccionDTO>> listarSecciones(@PathVariable Long paginaId) {
        return ResponseEntity.ok(sitioBuilderService.listarSecciones(paginaId));
    }

    @Operation(summary = "Agregar sección a una página")
    @PostMapping("/paginas/{paginaId}/secciones")
    public ResponseEntity<SeccionDTO> agregarSeccion(
            @PathVariable Long paginaId, @Valid @RequestBody SeccionCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sitioBuilderService.agregarSeccion(paginaId, dto));
    }

    @Operation(summary = "Actualizar sección")
    @PutMapping("/paginas/{paginaId}/secciones/{seccionId}")
    public ResponseEntity<SeccionDTO> actualizarSeccion(
            @PathVariable Long paginaId,
            @PathVariable Long seccionId,
            @Valid @RequestBody SeccionCreateDTO dto) {
        return ResponseEntity.ok(sitioBuilderService.actualizarSeccion(paginaId, seccionId, dto));
    }

    @Operation(summary = "Eliminar sección")
    @DeleteMapping("/paginas/{paginaId}/secciones/{seccionId}")
    public ResponseEntity<Void> eliminarSeccion(
            @PathVariable Long paginaId, @PathVariable Long seccionId) {
        sitioBuilderService.eliminarSeccion(paginaId, seccionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reordenar secciones de una página")
    @PatchMapping("/paginas/{paginaId}/secciones/reordenar")
    public ResponseEntity<List<SeccionDTO>> reordenar(
            @PathVariable Long paginaId, @RequestBody List<Long> ordenIds) {
        return ResponseEntity.ok(sitioBuilderService.reordenarSecciones(paginaId, ordenIds));
    }
}
