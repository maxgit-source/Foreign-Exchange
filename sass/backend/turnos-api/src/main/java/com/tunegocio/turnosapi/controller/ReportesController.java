package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.*;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.service.ExportService;
import com.tunegocio.turnosapi.service.PlanValidator;
import com.tunegocio.turnosapi.service.ReportesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Reportes", description = "KPIs, analytics y exportacion de datos del negocio")
@RestController
@RequestMapping("/api/reportes")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
@RequiredArgsConstructor
public class ReportesController {

    private final ReportesService reportesService;
    private final ExportService   exportService;
    private final PlanValidator   planValidator;

    // ─── Dashboard ───────────────────────────────────────────────────────────

    @Operation(summary = "Dashboard principal de KPIs del negocio")
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDTO> dashboard(
            @RequestParam(defaultValue = "30") int dias,
            @AuthenticationPrincipal Usuario usuario) {

        planValidator.validarPuedeVerReportes(tenant(usuario));
        return ResponseEntity.ok(reportesService.dashboard(dias, tenant(usuario)));
    }

    // ─── Ingresos ────────────────────────────────────────────────────────────

    @Operation(summary = "Reporte de ingresos por período con detalle diario")
    @GetMapping("/ingresos")
    public ResponseEntity<IngresoReporteDTO> ingresos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @AuthenticationPrincipal Usuario usuario) {

        planValidator.validarPuedeVerReportes(tenant(usuario));
        return ResponseEntity.ok(reportesService.ingresos(desde, hasta, tenant(usuario)));
    }

    // ─── Estadísticas de turnos ──────────────────────────────────────────────

    @Operation(summary = "Estadísticas de turnos por estado y tasas")
    @GetMapping("/turnos/estadisticas")
    public ResponseEntity<TurnoEstadisticasDTO> estadisticasTurnos(
            @RequestParam(defaultValue = "30") int dias,
            @AuthenticationPrincipal Usuario usuario) {

        planValidator.validarPuedeVerReportes(tenant(usuario));
        return ResponseEntity.ok(reportesService.estadisticasTurnos(dias, tenant(usuario)));
    }

    // ─── Top clientes ────────────────────────────────────────────────────────

    @Operation(summary = "Top clientes por cantidad de turnos")
    @GetMapping("/clientes/top")
    public ResponseEntity<List<ClienteKpiDTO>> topClientes(
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal Usuario usuario) {

        planValidator.validarPuedeVerReportes(tenant(usuario));
        return ResponseEntity.ok(reportesService.topClientes(Math.min(limit, 50)));
    }

    // ─── Popularidad de servicios ─────────────────────────────────────────────

    @Operation(summary = "Popularidad de servicios por cantidad de turnos")
    @GetMapping("/servicios/popularidad")
    public ResponseEntity<List<ServicioKpiDTO>> popularidadServicios(
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal Usuario usuario) {

        planValidator.validarPuedeVerReportes(tenant(usuario));
        return ResponseEntity.ok(reportesService.popularidadServicios(Math.min(limit, 50)));
    }

    // ─── Exportación CSV ─────────────────────────────────────────────────────

    @Operation(summary = "Exportar turnos del período en CSV o Excel")
    @GetMapping("/exportar/turnos")
    public ResponseEntity<byte[]> exportarTurnos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "CSV") FormatoExporte formato,
            @AuthenticationPrincipal Usuario usuario) {

        planValidator.validarPuedeVerReportes(tenant(usuario));

        List<TurnoResponseDTO> turnos = reportesService.turnosParaExportar(desde, hasta, tenant(usuario));

        return switch (formato) {
            case CSV -> {
                byte[] csv = exportService.exportarTurnosCSV(turnos);
                yield ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"turnos_" + desde + "_" + hasta + ".csv\"")
                        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                        .body(csv);
            }
            case EXCEL -> {
                byte[] xlsx = exportService.exportarTurnosExcel(turnos);
                yield ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"turnos_" + desde + "_" + hasta + ".xlsx\"")
                        .contentType(MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(xlsx);
            }
        };
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private Tenant tenant(Usuario usuario) {
        return usuario.getTenant();
    }
}
