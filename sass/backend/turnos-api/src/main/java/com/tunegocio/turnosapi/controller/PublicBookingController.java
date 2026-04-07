package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.*;
import com.tunegocio.turnosapi.entity.Role;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.repository.UsuarioRepository;
import com.tunegocio.turnosapi.service.DisponibilidadService;
import com.tunegocio.turnosapi.service.PlanValidator;
import com.tunegocio.turnosapi.service.PublicTenantResolver;
import com.tunegocio.turnosapi.service.ServicioService;
import com.tunegocio.turnosapi.service.TurnoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Tag(name = "Booking Público", description = "Endpoints sin autenticación para reservas")
@RestController
@RequestMapping("/public/{slug}")
@RequiredArgsConstructor
public class PublicBookingController {

    private final UsuarioRepository usuarioRepository;
    private final ServicioService servicioService;
    private final DisponibilidadService disponibilidadService;
    private final TurnoService turnoService;
    private final PlanValidator planValidator;
    private final PublicTenantResolver publicTenantResolver;

    @Operation(summary = "Información pública del negocio")
    @GetMapping
    public ResponseEntity<TenantPublicoDTO> info(@PathVariable String slug, HttpServletRequest request) {
        Tenant tenant = publicTenantResolver.resolve(slug, request);
        planValidator.validarPuedeUsarApiPublica(tenant);
        return ResponseEntity.ok(TenantPublicoDTO.builder()
                .nombre(tenant.getNombre())
                .slug(tenant.getSlug())
                .telefono(tenant.getTelefono())
                .logoUrl(tenant.getLogoUrl())
                .colorPrimario(tenant.getColorPrimario())
                .timezone(tenant.getTimezone())
                .build());
    }

    @Operation(summary = "Servicios disponibles del negocio")
    @GetMapping("/servicios")
    public ResponseEntity<List<ServicioResponseDTO>> servicios(@PathVariable String slug,
                                                               @RequestParam(required = false) Long categoriaId,
                                                               HttpServletRequest request) {
        Tenant tenant = publicTenantResolver.resolve(slug, request);
        planValidator.validarPuedeUsarApiPublica(tenant);
        return ResponseEntity.ok(servicioService.listarActivos(categoriaId));
    }

    @Operation(summary = "Profesionales del negocio disponibles para reservar")
    @GetMapping("/profesionales")
    public ResponseEntity<List<ProfesionalPublicoDTO>> profesionales(@PathVariable String slug,
                                                                     HttpServletRequest request) {
        Tenant tenant = publicTenantResolver.resolve(slug, request);
        planValidator.validarPuedeUsarApiPublica(tenant);
        List<ProfesionalPublicoDTO> profesionales = usuarioRepository
                .findByTenant_IdAndRoleInAndEnabledTrueOrderByNombreAsc(
                        tenant.getId(),
                        List.of(Role.OWNER, Role.STAFF)
                )
                .stream()
                .map(usuario -> ProfesionalPublicoDTO.builder()
                        .id(usuario.getId())
                        .nombre(usuario.getNombre())
                        .fotoUrl(usuario.getFotoUrl())
                        .build())
                .toList();
        return ResponseEntity.ok(profesionales);
    }

    @Operation(summary = "Slots disponibles para un profesional, servicios y fecha")
    @GetMapping("/slots")
    public ResponseEntity<List<SlotDisponibleDTO>> slots(
            @PathVariable String slug,
            @RequestParam Long profesionalId,
            @RequestParam(required = false) Long servicioId,
            @RequestParam(required = false) List<Long> servicioIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            HttpServletRequest request) {
        Tenant tenant = publicTenantResolver.resolve(slug, request);
        planValidator.validarPuedeUsarApiPublica(tenant);
        return ResponseEntity.ok(
                disponibilidadService.calcularSlots(
                        profesionalId,
                        mergeServicioIds(servicioId, servicioIds),
                        fecha,
                        tenant.getId()
                )
        );
    }

    @Operation(summary = "Reservar turno")
    @PostMapping("/reservar")
    public ResponseEntity<TurnoResponseDTO> reservar(
            @PathVariable String slug,
            @Valid @RequestBody ReservaPublicaRequestDTO dto,
            HttpServletRequest request) {
        Tenant tenant = publicTenantResolver.resolve(slug, request);

        TurnoResponseDTO turno = turnoService.crearPublico(
                dto.resolveServicioIds(),
                dto.getProfesionalId(),
                dto.getFechaHoraInicio(),
                dto.getNombreCliente(),
                dto.getApellidoCliente(),
                dto.getEmailCliente(),
                dto.getTelefonoCliente(),
                dto.getNotas(),
                tenant
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(turno);
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
