package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.DisponibilidadResponseDTO;
import com.tunegocio.turnosapi.dto.DisponibilidadUpsertDTO;
import com.tunegocio.turnosapi.dto.SlotDisponibleDTO;
import com.tunegocio.turnosapi.entity.*;
import com.tunegocio.turnosapi.exception.BusinessException;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.DisponibilidadRepository;
import com.tunegocio.turnosapi.repository.TurnoRepository;
import com.tunegocio.turnosapi.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisponibilidadService {

    private final DisponibilidadRepository disponibilidadRepository;
    private final TurnoRepository turnoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicioService servicioService;
    private final FechaBloqueadaService fechaBloqueadaService;
    private final TenantDateTimeMapper tenantDateTimeMapper;
    private final AuditService auditService;

    @Cacheable(value = "disponibilidad", key = "#profesionalId")
    @Transactional(readOnly = true)
    public List<DisponibilidadResponseDTO> obtenerDisponibilidad(Long profesionalId, Usuario actor) {
        validarProfesionalEnTenant(profesionalId, actor.getTenant().getId());

        return disponibilidadRepository.findByProfesional_IdOrderByDiaAscHoraInicioAsc(profesionalId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Cacheable(value = "slots", key = "#profesionalId + ':' + #fecha + ':' + #servicioIds")
    @Transactional(readOnly = true)
    public List<SlotDisponibleDTO> calcularSlots(Long profesionalId, List<Long> servicioIds,
                                                 LocalDate fecha, Long tenantId) {
        if (fecha.isBefore(LocalDate.now())) {
            throw new BusinessException("No se pueden consultar slots en fechas pasadas");
        }

        Usuario profesional = validarProfesionalEnTenant(profesionalId, tenantId);
        Tenant tenant = profesional.getTenant();
        List<Servicio> servicios = servicioService.buscarPorIdsActivos(servicioIds);
        int duracion = servicios.stream().mapToInt(Servicio::getDuracionMinutos).sum();

        if (fechaBloqueadaService.existeBloqueo(profesionalId, fecha)) {
            return List.of();
        }

        DiaSemana diaSemana = DiaSemana.from(fecha.getDayOfWeek());
        List<DisponibilidadProfesional> bloques =
                disponibilidadRepository.findByProfesional_IdAndDiaAndActivoTrueOrderByHoraInicioAsc(
                        profesional.getId(), diaSemana);

        if (bloques.isEmpty()) {
            return List.of();
        }

        Instant desde = tenantDateTimeMapper.startOfDay(fecha, tenant);
        Instant hasta = tenantDateTimeMapper.startOfNextDay(fecha, tenant);
        List<Turno> turnosExistentes = turnoRepository.findTurnosActivosByProfesionalYRango(
                profesionalId,
                desde,
                hasta,
                TurnoStatus.CANCELADO
        );

        LocalDateTime ahoraLocal = LocalDateTime.now(tenantDateTimeMapper.zoneId(tenant));
        List<SlotDisponibleDTO> slotsLibres = new ArrayList<>();

        for (DisponibilidadProfesional bloque : bloques) {
            LocalTime cursor = bloque.getHoraInicio();

            while (!cursor.plusMinutes(duracion).isAfter(bloque.getHoraFin())) {
                LocalTime slotFin = cursor.plusMinutes(duracion);
                LocalDateTime slotInicioLocal = LocalDateTime.of(fecha, cursor);
                LocalDateTime slotFinLocal = LocalDateTime.of(fecha, slotFin);

                boolean esPasado = fecha.isEqual(ahoraLocal.toLocalDate()) && slotInicioLocal.isBefore(ahoraLocal);

                if (!esPasado && !hayConflicto(slotInicioLocal, slotFinLocal, tenant, turnosExistentes)) {
                    slotsLibres.add(SlotDisponibleDTO.builder()
                            .horaInicio(cursor)
                            .horaFin(slotFin)
                            .build());
                }

                cursor = slotFin;
            }
        }

        return slotsLibres;
    }

    @CacheEvict(value = {"disponibilidad", "slots"}, allEntries = true)
    @Transactional
    public List<DisponibilidadResponseDTO> reemplazar(Long profesionalId,
                                                      List<DisponibilidadUpsertDTO> dtos,
                                                      Usuario actor) {
        Usuario profesional = validarProfesionalEnTenant(profesionalId, actor.getTenant().getId());

        validarSinSolapamientoInterno(dtos);

        disponibilidadRepository.deleteByProfesionalId(profesionalId);

        List<DisponibilidadProfesional> nuevos = dtos.stream()
                .map(dto -> {
                    if (!dto.getHoraFin().isAfter(dto.getHoraInicio())) {
                        throw new BusinessException(
                                "La hora de fin debe ser posterior a la hora de inicio para el día " + dto.getDia());
                    }

                    DisponibilidadProfesional disponibilidad = new DisponibilidadProfesional();
                    disponibilidad.setProfesional(profesional);
                    disponibilidad.setDia(dto.getDia());
                    disponibilidad.setHoraInicio(dto.getHoraInicio());
                    disponibilidad.setHoraFin(dto.getHoraFin());
                    disponibilidad.setActivo(dto.isActivo());
                    return disponibilidad;
                })
                .toList();

        List<DisponibilidadProfesional> guardados = disponibilidadRepository.saveAll(nuevos);
        auditService.log(
                "UPDATE",
                "DisponibilidadProfesional",
                profesionalId,
                actor,
                Map.of("bloques", guardados.size())
        );
        log.info("Disponibilidad actualizada: profesional={}, bloques={}", profesionalId, guardados.size());

        return guardados.stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public void validarReservaPermitida(Long profesionalId, Long tenantId,
                                        LocalDateTime inicio, LocalDateTime fin) {
        if (!inicio.toLocalDate().equals(fin.toLocalDate())) {
            throw new BusinessException("Los turnos deben comenzar y terminar el mismo día");
        }

        validarProfesionalEnTenant(profesionalId, tenantId);
        if (fechaBloqueadaService.existeBloqueo(profesionalId, inicio.toLocalDate())) {
            throw new BusinessException("El profesional tiene la fecha bloqueada");
        }

        DiaSemana diaSemana = DiaSemana.from(inicio.getDayOfWeek());

        List<DisponibilidadProfesional> bloques = disponibilidadRepository
                .findByProfesional_IdAndDiaAndActivoTrueOrderByHoraInicioAsc(profesionalId, diaSemana);

        boolean dentroDeDisponibilidad = bloques.stream().anyMatch(bloque ->
                !bloque.getHoraInicio().isAfter(inicio.toLocalTime()) &&
                        !bloque.getHoraFin().isBefore(fin.toLocalTime())
        );

        if (!dentroDeDisponibilidad) {
            throw new BusinessException("El horario seleccionado está fuera de la disponibilidad configurada");
        }
    }

    private boolean hayConflicto(LocalDateTime slotInicio, LocalDateTime slotFin,
                                 Tenant tenant, List<Turno> turnos) {
        Instant inicio = tenantDateTimeMapper.toInstant(slotInicio, tenant);
        Instant fin = tenantDateTimeMapper.toInstant(slotFin, tenant);

        return turnos.stream().anyMatch(turno ->
                turno.getFechaHoraInicio().isBefore(fin) && turno.getFechaHoraFin().isAfter(inicio)
        );
    }

    private void validarSinSolapamientoInterno(List<DisponibilidadUpsertDTO> dtos) {
        for (int i = 0; i < dtos.size(); i++) {
            for (int j = i + 1; j < dtos.size(); j++) {
                DisponibilidadUpsertDTO a = dtos.get(i);
                DisponibilidadUpsertDTO b = dtos.get(j);
                if (a.getDia() == b.getDia()
                        && a.getHoraInicio().isBefore(b.getHoraFin())
                        && a.getHoraFin().isAfter(b.getHoraInicio())) {
                    throw new BusinessException("Los bloques del día " + a.getDia() + " se superponen entre sí");
                }
            }
        }
    }

    private Usuario validarProfesionalEnTenant(Long profesionalId, Long tenantId) {
        Usuario profesional = usuarioRepository.findByIdAndTenant_IdAndRoleIn(
                        profesionalId,
                        tenantId,
                        List.of(Role.OWNER, Role.STAFF)
                )
                .orElseThrow(() -> new ResourceNotFoundException("Profesional", "id", profesionalId));

        if (!profesional.isEnabled()) {
            throw new BusinessException("El profesional seleccionado no está activo");
        }
        return profesional;
    }

    private DisponibilidadResponseDTO toDTO(DisponibilidadProfesional disponibilidad) {
        return DisponibilidadResponseDTO.builder()
                .id(disponibilidad.getId())
                .dia(disponibilidad.getDia())
                .horaInicio(disponibilidad.getHoraInicio())
                .horaFin(disponibilidad.getHoraFin())
                .activo(disponibilidad.isActivo())
                .build();
    }
}
