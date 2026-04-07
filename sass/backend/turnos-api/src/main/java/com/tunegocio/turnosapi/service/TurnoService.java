package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.ReprogramarTurnoDTO;
import com.tunegocio.turnosapi.dto.TurnoHistorialDTO;
import com.tunegocio.turnosapi.dto.TurnoRequestDTO;
import com.tunegocio.turnosapi.dto.TurnoResponseDTO;
import com.tunegocio.turnosapi.entity.*;
import com.tunegocio.turnosapi.exception.BusinessException;
import com.tunegocio.turnosapi.exception.ConflictException;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.TurnoHistorialRepository;
import com.tunegocio.turnosapi.repository.TurnoRepository;
import com.tunegocio.turnosapi.repository.UsuarioRepository;
import com.tunegocio.turnosapi.specification.TurnoSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TurnoService {

    private final TurnoRepository turnoRepository;
    private final TurnoHistorialRepository turnoHistorialRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteService clienteService;
    private final ServicioService servicioService;
    private final DisponibilidadService disponibilidadService;
    private final NotificacionService notificacionService;
    private final TenantDateTimeMapper tenantDateTimeMapper;
    private final TurnoMapper turnoMapper;
    private final PlanValidator planValidator;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<TurnoResponseDTO> listar(LocalDate fechaInicio, LocalDate fechaFin,
                                         Long profesionalId, Long clienteId,
                                         TurnoStatus estado, Pageable pageable,
                                         Usuario actor) {
        Tenant tenant = actor.getTenant();
        Specification<Turno> spec = Specification.where(TurnoSpecification.conFetchCompleto());

        if (fechaInicio != null) {
            spec = spec.and(TurnoSpecification.desdeFecha(tenantDateTimeMapper.startOfDay(fechaInicio, tenant)));
        }
        if (fechaFin != null) {
            spec = spec.and(TurnoSpecification.hastaFecha(tenantDateTimeMapper.startOfNextDay(fechaFin, tenant)));
        }
        if (profesionalId != null) {
            spec = spec.and(TurnoSpecification.tieneProfesional(profesionalId));
        }
        if (clienteId != null) {
            spec = spec.and(TurnoSpecification.tieneCliente(clienteId));
        }
        if (estado != null) {
            spec = spec.and(TurnoSpecification.tieneEstado(estado));
        }

        return turnoRepository.findAll(spec, pageable)
                .map(turno -> turnoMapper.toResponseDTO(turno, tenant));
    }

    @Transactional(readOnly = true)
    public TurnoResponseDTO obtenerPorId(Long id, Usuario actor) {
        return turnoMapper.toResponseDTO(buscarPorId(id), actor.getTenant());
    }

    @Transactional(readOnly = true)
    public List<TurnoResponseDTO> agendaDelDia(LocalDate fecha, Usuario actor) {
        Tenant tenant = actor.getTenant();
        Specification<Turno> spec = Specification.where(TurnoSpecification.desdeFecha(tenantDateTimeMapper.startOfDay(fecha, tenant)))
                .and(TurnoSpecification.hastaFecha(tenantDateTimeMapper.startOfNextDay(fecha, tenant)))
                .and(TurnoSpecification.conFetchCompleto());

        return turnoRepository.findAll(spec).stream()
                .sorted(Comparator.comparing(Turno::getFechaHoraInicio))
                .map(turno -> turnoMapper.toResponseDTO(turno, tenant))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TurnoHistorialDTO> historial(Long id, Usuario actor) {
        Turno turno = buscarPorId(id);
        Tenant tenant = actor.getTenant();
        List<TurnoHistorial> historial = turnoHistorialRepository.findByTurno_IdOrderByCreatedAtDesc(turno.getId());

        Map<Long, String> usuarios = usuarioRepository.findAllById(
                        historial.stream()
                                .map(TurnoHistorial::getUsuarioId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList()
                ).stream()
                .collect(HashMap::new, (map, usuario) -> map.put(usuario.getId(), usuario.getNombre()), HashMap::putAll);

        return historial.stream()
                .map(entry -> TurnoHistorialDTO.builder()
                        .id(entry.getId())
                        .estadoAnterior(entry.getEstadoAnterior() != null ? entry.getEstadoAnterior().name() : null)
                        .estadoNuevo(entry.getEstadoNuevo().name())
                        .usuarioId(entry.getUsuarioId())
                        .usuarioNombre(resolveNombreUsuario(entry.getUsuarioId(), usuarios))
                        .fechaAnterior(entry.getFechaAnterior() != null ? tenantDateTimeMapper.toLocalDateTime(entry.getFechaAnterior(), tenant) : null)
                        .fechaNueva(entry.getFechaNueva() != null ? tenantDateTimeMapper.toLocalDateTime(entry.getFechaNueva(), tenant) : null)
                        .notas(entry.getNotas())
                        .createdAt(entry.getCreatedAt() != null ? tenantDateTimeMapper.toLocalDateTime(entry.getCreatedAt(), tenant) : null)
                        .build())
                .toList();
    }

    @Transactional
    public TurnoResponseDTO crear(TurnoRequestDTO dto, Usuario actor) {
        Tenant tenant = actor.getTenant();
        planValidator.validarPuedeCrearTurno(tenant);
        Cliente cliente = clienteService.buscarPorId(dto.getClienteId());
        Usuario profesional = buscarProfesionalEnTenant(dto.getProfesionalId(), tenant.getId());
        List<Servicio> servicios = servicioService.buscarPorIdsActivos(dto.resolveServicioIds());
        List<LocalDateTime> ocurrencias = resolveOcurrencias(dto.getFechaHoraInicio(), dto.getRecurrente(), dto.getSemanas());

        validarOcurrencias(ocurrencias, servicios, profesional, tenant);

        List<Turno> guardados = new ArrayList<>();
        Turno padre = null;
        boolean serieRecurrente = ocurrencias.size() > 1;

        for (int i = 0; i < ocurrencias.size(); i++) {
            LocalDateTime inicioLocal = ocurrencias.get(i);
            LocalDateTime finLocal = inicioLocal.plusMinutes(sumarDuracion(servicios));
            Instant inicio = tenantDateTimeMapper.toInstant(inicioLocal, tenant);
            Instant fin = tenantDateTimeMapper.toInstant(finLocal, tenant);

            Turno turno = buildTurno(
                    cliente,
                    profesional,
                    servicios,
                    inicio,
                    fin,
                    dto.getNotas(),
                    i == 0 ? null : padre,
                    serieRecurrente,
                    i == 0 && serieRecurrente ? dto.getSemanas() : null
            );

            Turno guardado = guardarTraduciendoConflictos(turno);
            if (i == 0) {
                padre = guardado;
            }

            registrarHistorial(
                    guardado,
                    null,
                    guardado.getEstado(),
                    actor != null ? actor.getId() : null,
                    null,
                    guardado.getFechaHoraInicio(),
                    i == 0 ? "Creación inicial" : "Instancia recurrente"
            );
            guardados.add(guardado);
        }

        auditService.log(
                "CREATE",
                "Turno",
                padre.getId(),
                actor,
                Map.of(
                        "inicio", padre.getFechaHoraInicio().toString(),
                        "cantidadInstancias", guardados.size(),
                        "servicioIds", dto.resolveServicioIds()
                )
        );
        guardados.forEach(notificacionService::enviarConfirmacionTurno);
        return turnoMapper.toResponseDTO(padre, tenant);
    }

    @Transactional
    public TurnoResponseDTO crearPublico(List<Long> servicioIds,
                                         Long profesionalId,
                                         LocalDateTime inicioLocal,
                                         String nombreCliente, String apellidoCliente,
                                         String emailCliente, String telefonoCliente,
                                         String notas, Tenant tenant) {
        planValidator.validarPuedeCrearTurno(tenant);
        Usuario profesional = buscarProfesionalEnTenant(profesionalId, tenant.getId());
        List<Servicio> servicios = servicioService.buscarPorIdsActivos(servicioIds);

        validarOcurrencias(List.of(inicioLocal), servicios, profesional, tenant);
        Cliente cliente = clienteService.buscarOCrear(emailCliente, nombreCliente, apellidoCliente, telefonoCliente, tenant);

        LocalDateTime finLocal = inicioLocal.plusMinutes(sumarDuracion(servicios));
        Turno guardado = guardarTraduciendoConflictos(buildTurno(
                cliente,
                profesional,
                servicios,
                tenantDateTimeMapper.toInstant(inicioLocal, tenant),
                tenantDateTimeMapper.toInstant(finLocal, tenant),
                notas,
                null,
                false,
                null
        ));

        registrarHistorial(guardado, null, guardado.getEstado(), null, null, guardado.getFechaHoraInicio(), "Reserva pública");
        auditService.log(
                "CREATE",
                "Turno",
                guardado.getId(),
                tenant,
                null,
                Map.of("publico", true, "inicio", guardado.getFechaHoraInicio().toString(), "servicioIds", servicioIds)
        );
        notificacionService.enviarConfirmacionTurno(guardado);
        return turnoMapper.toResponseDTO(guardado, tenant);
    }

    @Transactional
    public TurnoResponseDTO confirmar(Long id, Usuario actor) {
        Turno turno = buscarPorId(id);
        validarTransicion(turno, TurnoStatus.CONFIRMADO);
        TurnoStatus anterior = turno.getEstado();
        turno.setEstado(TurnoStatus.CONFIRMADO);
        Turno guardado = turnoRepository.save(turno);
        registrarHistorial(guardado, anterior, guardado.getEstado(), actor.getId(), guardado.getFechaHoraInicio(), guardado.getFechaHoraInicio(), "Cambio de estado");
        auditService.log("UPDATE", "Turno", guardado.getId(), actor, Map.of("estado", guardado.getEstado().name()));
        return turnoMapper.toResponseDTO(guardado, actor.getTenant());
    }

    @Transactional
    public TurnoResponseDTO cancelar(Long id, Usuario actor) {
        Turno turno = buscarPorId(id);
        validarTransicion(turno, TurnoStatus.CANCELADO);
        TurnoStatus anterior = turno.getEstado();
        turno.setEstado(TurnoStatus.CANCELADO);
        Turno guardado = turnoRepository.save(turno);
        registrarHistorial(guardado, anterior, guardado.getEstado(), actor.getId(), guardado.getFechaHoraInicio(), guardado.getFechaHoraInicio(), "Cambio de estado");
        auditService.log("UPDATE", "Turno", guardado.getId(), actor, Map.of("estado", guardado.getEstado().name()));
        notificacionService.enviarCancelacion(guardado);
        return turnoMapper.toResponseDTO(guardado, actor.getTenant());
    }

    @Transactional
    public TurnoResponseDTO completar(Long id, Usuario actor) {
        Turno turno = buscarPorId(id);
        validarTransicion(turno, TurnoStatus.COMPLETADO);
        TurnoStatus anterior = turno.getEstado();
        turno.setEstado(TurnoStatus.COMPLETADO);
        Turno guardado = turnoRepository.save(turno);
        registrarHistorial(guardado, anterior, guardado.getEstado(), actor.getId(), guardado.getFechaHoraInicio(), guardado.getFechaHoraInicio(), "Cambio de estado");
        auditService.log("UPDATE", "Turno", guardado.getId(), actor, Map.of("estado", guardado.getEstado().name()));
        return turnoMapper.toResponseDTO(guardado, actor.getTenant());
    }

    @Transactional
    public TurnoResponseDTO marcarNoShow(Long id, Usuario actor) {
        Turno turno = buscarPorId(id);
        validarTransicion(turno, TurnoStatus.NO_SHOW);
        TurnoStatus anterior = turno.getEstado();
        turno.setEstado(TurnoStatus.NO_SHOW);
        Turno guardado = turnoRepository.save(turno);
        registrarHistorial(guardado, anterior, guardado.getEstado(), actor.getId(), guardado.getFechaHoraInicio(), guardado.getFechaHoraInicio(), "Cambio de estado");
        auditService.log("UPDATE", "Turno", guardado.getId(), actor, Map.of("estado", guardado.getEstado().name()));
        return turnoMapper.toResponseDTO(guardado, actor.getTenant());
    }

    @Transactional
    public TurnoResponseDTO reprogramar(Long id, ReprogramarTurnoDTO dto, Usuario actor) {
        Turno turno = buscarPorId(id);

        if (turno.getEstado() != TurnoStatus.PENDIENTE && turno.getEstado() != TurnoStatus.CONFIRMADO) {
            throw new BusinessException("Solo se pueden reprogramar turnos pendientes o confirmados");
        }

        Tenant tenant = actor.getTenant();
        LocalDateTime nuevoInicioLocal = dto.getNuevaFechaHoraInicio();
        LocalDateTime nuevoFinLocal = nuevoInicioLocal.plusMinutes(turno.getDuracionTotalMinutos());
        Instant nuevoInicio = tenantDateTimeMapper.toInstant(nuevoInicioLocal, tenant);
        Instant nuevoFin = tenantDateTimeMapper.toInstant(nuevoFinLocal, tenant);

        if (nuevoInicio.isBefore(Instant.now())) {
            throw new BusinessException("No se puede reprogramar un turno al pasado");
        }

        disponibilidadService.validarReservaPermitida(
                turno.getProfesional().getId(),
                actor.getTenant().getId(),
                nuevoInicioLocal,
                nuevoFinLocal
        );

        boolean hayConflicto = turnoRepository.existeSolapamientoExcluyendoTurno(
                turno.getProfesional().getId(),
                turno.getId(),
                nuevoInicio,
                nuevoFin,
                TurnoStatus.CANCELADO
        );
        if (hayConflicto) {
            throw new ConflictException("El nuevo horario se superpone con otro turno existente");
        }

        Instant fechaAnterior = turno.getFechaHoraInicio();
        turno.setFechaHoraInicio(nuevoInicio);
        turno.setFechaHoraFin(nuevoFin);
        turno.setRecordatorio24hEnviado(false);

        Turno guardado = guardarTraduciendoConflictos(turno);
        registrarHistorial(guardado, guardado.getEstado(), guardado.getEstado(), actor.getId(), fechaAnterior, nuevoInicio, "Reprogramación");
        auditService.log(
                "UPDATE",
                "Turno",
                guardado.getId(),
                actor,
                Map.of("reprogramadoA", guardado.getFechaHoraInicio().toString())
        );
        notificacionService.enviarReprogramacion(guardado);
        return turnoMapper.toResponseDTO(guardado, tenant);
    }

    @Transactional(readOnly = true)
    Turno buscarPorId(Long id) {
        return turnoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Turno", id));
    }

    private void validarOcurrencias(List<LocalDateTime> ocurrencias,
                                    List<Servicio> servicios,
                                    Usuario profesional,
                                    Tenant tenant) {
        int duracionTotal = sumarDuracion(servicios);

        for (LocalDateTime inicioLocal : ocurrencias) {
            LocalDateTime finLocal = inicioLocal.plusMinutes(duracionTotal);
            Instant inicio = tenantDateTimeMapper.toInstant(inicioLocal, tenant);
            Instant fin = tenantDateTimeMapper.toInstant(finLocal, tenant);

            if (inicio.isBefore(Instant.now())) {
                throw new BusinessException("No se pueden crear turnos en el pasado");
            }

            disponibilidadService.validarReservaPermitida(profesional.getId(), tenant.getId(), inicioLocal, finLocal);

            boolean hayConflicto = turnoRepository.existeSolapamiento(
                    profesional.getId(),
                    inicio,
                    fin,
                    TurnoStatus.CANCELADO
            );
            if (hayConflicto) {
                throw new ConflictException(
                        "El horario seleccionado no está disponible. El profesional ya tiene un turno en ese momento."
                );
            }
        }
    }

    private void validarTransicion(Turno turno, TurnoStatus nuevoEstado) {
        TurnoStatus actual = turno.getEstado();

        boolean invalido = switch (nuevoEstado) {
            case CONFIRMADO -> actual != TurnoStatus.PENDIENTE;
            case CANCELADO -> actual == TurnoStatus.COMPLETADO || actual == TurnoStatus.CANCELADO;
            case COMPLETADO -> actual != TurnoStatus.CONFIRMADO && actual != TurnoStatus.PENDIENTE;
            case NO_SHOW -> actual != TurnoStatus.CONFIRMADO && actual != TurnoStatus.PENDIENTE;
            default -> true;
        };

        if (invalido) {
            throw new BusinessException(
                    "No se puede cambiar el estado de '" + actual + "' a '" + nuevoEstado + "'"
            );
        }
    }

    private Usuario buscarProfesionalEnTenant(Long profesionalId, Long tenantId) {
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

    private Turno guardarTraduciendoConflictos(Turno turno) {
        try {
            return turnoRepository.save(turno);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("El horario seleccionado ya no está disponible");
        }
    }

    private Turno buildTurno(Cliente cliente, Usuario profesional,
                             List<Servicio> servicios, Instant inicio,
                             Instant fin, String notas, Turno turnoPadre,
                             boolean recurrente, Integer recurrenciaSemanas) {
        Turno turno = new Turno();
        turno.setCliente(cliente);
        turno.setProfesional(profesional);
        turno.getServicios().addAll(servicios);
        turno.setFechaHoraInicio(inicio);
        turno.setFechaHoraFin(fin);
        turno.setEstado(TurnoStatus.PENDIENTE);
        turno.setTurnoPadre(turnoPadre);
        turno.setRecurrente(recurrente);
        turno.setRecurrenciaSemanas(recurrenciaSemanas);
        turno.setRecordatorio24hEnviado(false);
        turno.setNotas(notas);
        return turno;
    }

    private List<LocalDateTime> resolveOcurrencias(LocalDateTime inicioBase, Boolean recurrente, Integer semanas) {
        List<LocalDateTime> ocurrencias = new ArrayList<>();
        ocurrencias.add(inicioBase);

        if (Boolean.TRUE.equals(recurrente)) {
            if (semanas == null || semanas < 1 || semanas > 52) {
                throw new BusinessException("La recurrencia debe indicar entre 1 y 52 semanas");
            }
            for (int i = 1; i <= semanas; i++) {
                ocurrencias.add(inicioBase.plusWeeks(i));
            }
        }

        return ocurrencias;
    }

    private int sumarDuracion(List<Servicio> servicios) {
        return servicios.stream()
                .mapToInt(Servicio::getDuracionMinutos)
                .sum();
    }

    private void registrarHistorial(Turno turno,
                                    TurnoStatus estadoAnterior,
                                    TurnoStatus estadoNuevo,
                                    Long usuarioId,
                                    Instant fechaAnterior,
                                    Instant fechaNueva,
                                    String notas) {
        TurnoHistorial historial = new TurnoHistorial();
        historial.setTurno(turno);
        historial.setEstadoAnterior(estadoAnterior);
        historial.setEstadoNuevo(estadoNuevo);
        historial.setUsuarioId(usuarioId);
        historial.setFechaAnterior(fechaAnterior);
        historial.setFechaNueva(fechaNueva);
        historial.setNotas(notas);
        turnoHistorialRepository.save(historial);
    }

    private String resolveNombreUsuario(Long usuarioId, Map<Long, String> usuarios) {
        if (usuarioId == null) {
            return "Reserva pública";
        }
        return usuarios.getOrDefault(usuarioId, "Usuario");
    }
}
