package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.ServicioRequestDTO;
import com.tunegocio.turnosapi.dto.ServicioResponseDTO;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.service.ServicioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Servicios", description = "Catálogo de servicios del negocio")
@RestController
@RequestMapping("/api/servicios")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
@RequiredArgsConstructor
public class ServicioController {

    private final ServicioService servicioService;

    @Operation(summary = "Listar servicios activos")
    @GetMapping
    public ResponseEntity<List<ServicioResponseDTO>> listar(@RequestParam(required = false) Long categoriaId) {
        return ResponseEntity.ok(servicioService.listarActivos(categoriaId));
    }

    @Operation(summary = "Listar todos los servicios")
    @GetMapping("/todos")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<ServicioResponseDTO>> listarTodos(@RequestParam(required = false) Long categoriaId) {
        return ResponseEntity.ok(servicioService.listarTodos(categoriaId));
    }

    @Operation(summary = "Obtener servicio por ID")
    @GetMapping("/{id}")
    public ResponseEntity<ServicioResponseDTO> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(servicioService.obtenerPorId(id));
    }

    @Operation(summary = "Crear nuevo servicio")
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ServicioResponseDTO> crear(@Valid @RequestBody ServicioRequestDTO dto,
                                                     @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicioService.crear(dto, actor));
    }

    @Operation(summary = "Actualizar servicio")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ServicioResponseDTO> actualizar(@PathVariable Long id,
                                                          @Valid @RequestBody ServicioRequestDTO dto,
                                                          @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(servicioService.actualizar(id, dto, actor));
    }

    @Operation(summary = "Subir imagen del servicio")
    @PostMapping(value = "/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ServicioResponseDTO> actualizarImagen(@PathVariable Long id,
                                                                @RequestPart("file") MultipartFile file,
                                                                @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(servicioService.actualizarImagen(id, file, actor));
    }

    @Operation(summary = "Desactivar servicio")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id,
                                         @AuthenticationPrincipal Usuario actor) {
        servicioService.eliminar(id, actor);
        return ResponseEntity.noContent().build();
    }
}
