package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.ReprogramarTurnoDTO;
import com.tunegocio.turnosapi.dto.TurnoHistorialDTO;
import com.tunegocio.turnosapi.dto.TurnoRequestDTO;
import com.tunegocio.turnosapi.dto.TurnoResponseDTO;
import com.tunegocio.turnosapi.entity.TurnoStatus;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.service.TurnoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Turnos", description = "Gestión completa del ciclo de vida de los turnos")
@RestController
@RequestMapping("/api/turnos")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
@RequiredArgsConstructor
public class TurnoController {

    private final TurnoService turnoService;

    @Operation(summary = "Listar turnos con filtros y paginación")
    @GetMapping
    public ResponseEntity<Page<TurnoResponseDTO>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) Long profesionalId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) TurnoStatus estado,
            @PageableDefault(size = 20, sort = "fechaHoraInicio", direction = Sort.Direction.ASC) Pageable pageable,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(
                turnoService.listar(fechaInicio, fechaFin, profesionalId, clienteId, estado, pageable, actor)
        );
    }

    @Operation(summary = "Agenda del día")
    @GetMapping("/agenda")
    public ResponseEntity<List<TurnoResponseDTO>> agendaDelDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @AuthenticationPrincipal Usuario actor) {
        LocalDate fechaConsulta = fecha != null ? fecha : LocalDate.now();
        return ResponseEntity.ok(turnoService.agendaDelDia(fechaConsulta, actor));
    }

    @Operation(summary = "Obtener turno por ID")
    @GetMapping("/{id}")
    public ResponseEntity<TurnoResponseDTO> obtener(@PathVariable Long id,
                                                    @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(turnoService.obtenerPorId(id, actor));
    }

    @Operation(summary = "Ver historial del turno")
    @GetMapping("/{id}/historial")
    public ResponseEntity<List<TurnoHistorialDTO>> historial(@PathVariable Long id,
                                                             @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(turnoService.historial(id, actor));
    }

    @Operation(summary = "Crear turno")
    @PostMapping
    public ResponseEntity<TurnoResponseDTO> crear(@Valid @RequestBody TurnoRequestDTO dto,
                                                  @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(turnoService.crear(dto, actor));
    }

    @Operation(summary = "Confirmar turno")
    @PatchMapping("/{id}/confirmar")
    public ResponseEntity<TurnoResponseDTO> confirmar(@PathVariable Long id,
                                                      @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(turnoService.confirmar(id, actor));
    }

    @Operation(summary = "Cancelar turno")
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<TurnoResponseDTO> cancelar(@PathVariable Long id,
                                                     @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(turnoService.cancelar(id, actor));
    }

    @Operation(summary = "Marcar turno como completado")
    @PatchMapping("/{id}/completar")
    public ResponseEntity<TurnoResponseDTO> completar(@PathVariable Long id,
                                                      @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(turnoService.completar(id, actor));
    }

    @Operation(summary = "Marcar no-show")
    @PatchMapping("/{id}/no-show")
    public ResponseEntity<TurnoResponseDTO> noShow(@PathVariable Long id,
                                                   @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(turnoService.marcarNoShow(id, actor));
    }

    @Operation(summary = "Reprogramar turno")
    @PatchMapping("/{id}/reprogramar")
    public ResponseEntity<TurnoResponseDTO> reprogramar(@PathVariable Long id,
                                                        @Valid @RequestBody ReprogramarTurnoDTO dto,
                                                        @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(turnoService.reprogramar(id, dto, actor));
    }
}
