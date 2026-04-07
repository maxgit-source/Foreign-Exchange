package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.*;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.service.DisponibilidadService;
import com.tunegocio.turnosapi.service.FechaBloqueadaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Tag(name = "Disponibilidad", description = "Horarios de trabajo, bloqueos y slots disponibles")
@RestController
@RequestMapping("/api/disponibilidad")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
@RequiredArgsConstructor
public class DisponibilidadController {

    private final DisponibilidadService disponibilidadService;
    private final FechaBloqueadaService fechaBloqueadaService;

    @Operation(summary = "Ver disponibilidad semanal de un profesional")
    @GetMapping("/{profesionalId}")
    public ResponseEntity<List<DisponibilidadResponseDTO>> obtener(
            @PathVariable Long profesionalId,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(disponibilidadService.obtenerDisponibilidad(profesionalId, actor));
    }

    @Operation(summary = "Configurar disponibilidad de un profesional")
    @PutMapping("/{profesionalId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<DisponibilidadResponseDTO>> reemplazar(
            @PathVariable Long profesionalId,
            @Valid @RequestBody List<DisponibilidadUpsertDTO> dtos,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(disponibilidadService.reemplazar(profesionalId, dtos, actor));
    }

    @Operation(summary = "Listar fechas bloqueadas de un profesional")
    @GetMapping("/{profesionalId}/bloqueados")
    public ResponseEntity<List<FechaBloqueadaResponseDTO>> bloqueados(@PathVariable Long profesionalId,
                                                                      @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(fechaBloqueadaService.listar(profesionalId, actor));
    }

    @Operation(summary = "Bloquear fechas para un profesional")
    @PostMapping("/{profesionalId}/bloquear")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<FechaBloqueadaResponseDTO> bloquear(@PathVariable Long profesionalId,
                                                              @Valid @RequestBody FechaBloqueadaRequestDTO dto,
                                                              @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fechaBloqueadaService.bloquear(profesionalId, dto, actor));
    }

    @Operation(summary = "Eliminar un bloqueo de fechas")
    @DeleteMapping("/bloqueados/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> eliminarBloqueo(@PathVariable Long id,
                                                @AuthenticationPrincipal Usuario actor) {
        fechaBloqueadaService.eliminar(id, actor);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Calcular slots disponibles para una fecha")
    @GetMapping("/{profesionalId}/slots")
    public ResponseEntity<List<SlotDisponibleDTO>> slots(
            @PathVariable Long profesionalId,
            @RequestParam(required = false) Long servicioId,
            @RequestParam(required = false) List<Long> servicioIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(
                disponibilidadService.calcularSlots(
                        profesionalId,
                        mergeServicioIds(servicioId, servicioIds),
                        fecha,
                        actor.getTenant().getId()
                )
        );
    }

    private List<Long> mergeServicioIds(Long servicioId, List<Long> servicioIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (servicioIds != null) {
            servicioIds.stream()
                    .filter(id -> id != null && id > 0)
                    .forEach(ids::add);
        }
        if (servicioId != null && servicioId > 0) {
            ids.add(servicioId);
        }
        return new ArrayList<>(ids);
    }
}
