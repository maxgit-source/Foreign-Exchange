package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.SlotDisponibleDTO;
import com.tunegocio.turnosapi.entity.DiaSemana;
import com.tunegocio.turnosapi.entity.DisponibilidadProfesional;
import com.tunegocio.turnosapi.entity.Role;
import com.tunegocio.turnosapi.entity.Servicio;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Turno;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.exception.BusinessException;
import com.tunegocio.turnosapi.repository.DisponibilidadRepository;
import com.tunegocio.turnosapi.repository.TurnoRepository;
import com.tunegocio.turnosapi.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisponibilidadServiceTest {

    @Mock
    private DisponibilidadRepository disponibilidadRepository;
    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ServicioService servicioService;
    @Mock
    private FechaBloqueadaService fechaBloqueadaService;
    @Mock
    private AuditService auditService;

    private DisponibilidadService disponibilidadService;
    private TenantDateTimeMapper tenantDateTimeMapper;

    private Usuario profesional;
    private Servicio corte;
    private Servicio color;

    @BeforeEach
    void setUp() {
        tenantDateTimeMapper = new TenantDateTimeMapper();
        disponibilidadService = new DisponibilidadService(
                disponibilidadRepository,
                turnoRepository,
                usuarioRepository,
                servicioService,
                fechaBloqueadaService,
                tenantDateTimeMapper,
                auditService
        );

        profesional = profesional(10L, 1L);
        corte = servicio(20L, "Corte", 60, 15000);
        color = servicio(21L, "Color", 45, 22000);

        when(usuarioRepository.findByIdAndTenant_IdAndRoleIn(eq(profesional.getId()), eq(1L), anyList()))
                .thenReturn(Optional.of(profesional));
        when(fechaBloqueadaService.existeBloqueo(anyLong(), any(LocalDate.class))).thenReturn(false);
    }

    @Test
    void calcularSlots_debeExcluirTurnosExistentes() {
        LocalDate fecha = LocalDate.now(tenantDateTimeMapper.zoneId(profesional.getTenant())).plusDays(1);
        DiaSemana dia = DiaSemana.from(fecha.getDayOfWeek());

        when(servicioService.buscarPorIdsActivos(List.of(corte.getId()))).thenReturn(List.of(corte));
        when(disponibilidadRepository.findByProfesional_IdAndDiaAndActivoTrueOrderByHoraInicioAsc(profesional.getId(), dia))
                .thenReturn(List.of(bloque(dia, LocalTime.of(9, 0), LocalTime.of(12, 0))));
        when(turnoRepository.findTurnosActivosByProfesionalYRango(anyLong(), any(), any(), any()))
                .thenReturn(List.of(turno(fecha.atTime(10, 0), fecha.atTime(11, 0))));

        List<SlotDisponibleDTO> slots = disponibilidadService.calcularSlots(
                profesional.getId(),
                List.of(corte.getId()),
                fecha,
                1L
        );

        assertEquals(2, slots.size());
        assertEquals(LocalTime.of(9, 0), slots.get(0).getHoraInicio());
        assertEquals(LocalTime.of(11, 0), slots.get(1).getHoraInicio());
    }

    @Test
    void calcularSlots_debeUsarDuracionTotalDeMultiplesServicios() {
        LocalDate fecha = LocalDate.now(tenantDateTimeMapper.zoneId(profesional.getTenant())).plusDays(2);
        DiaSemana dia = DiaSemana.from(fecha.getDayOfWeek());

        when(servicioService.buscarPorIdsActivos(List.of(corte.getId(), color.getId())))
                .thenReturn(List.of(corte, color));
        when(disponibilidadRepository.findByProfesional_IdAndDiaAndActivoTrueOrderByHoraInicioAsc(profesional.getId(), dia))
                .thenReturn(List.of(bloque(dia, LocalTime.of(9, 0), LocalTime.of(12, 0))));
        when(turnoRepository.findTurnosActivosByProfesionalYRango(anyLong(), any(), any(), any()))
                .thenReturn(List.of());

        List<SlotDisponibleDTO> slots = disponibilidadService.calcularSlots(
                profesional.getId(),
                List.of(corte.getId(), color.getId()),
                fecha,
                1L
        );

        assertEquals(1, slots.size());
        assertEquals(LocalTime.of(9, 0), slots.get(0).getHoraInicio());
        assertEquals(LocalTime.of(10, 45), slots.get(0).getHoraFin());
    }

    @Test
    void calcularSlots_debeRetornarVacioSiLaFechaEstaBloqueada() {
        LocalDate fecha = LocalDate.now(tenantDateTimeMapper.zoneId(profesional.getTenant())).plusDays(3);

        when(servicioService.buscarPorIdsActivos(List.of(corte.getId()))).thenReturn(List.of(corte));
        when(fechaBloqueadaService.existeBloqueo(profesional.getId(), fecha)).thenReturn(true);

        List<SlotDisponibleDTO> slots = disponibilidadService.calcularSlots(
                profesional.getId(),
                List.of(corte.getId()),
                fecha,
                1L
        );

        assertTrue(slots.isEmpty());
        verify(turnoRepository, never()).findTurnosActivosByProfesionalYRango(anyLong(), any(), any(), any());
    }

    @Test
    void validarReservaPermitida_debeRechazarFueraDeDisponibilidad() {
        LocalDateTime inicio = LocalDate.now(tenantDateTimeMapper.zoneId(profesional.getTenant())).plusDays(1).atTime(18, 0);
        LocalDateTime fin = inicio.plusMinutes(60);
        DiaSemana dia = DiaSemana.from(inicio.getDayOfWeek());

        when(disponibilidadRepository.findByProfesional_IdAndDiaAndActivoTrueOrderByHoraInicioAsc(profesional.getId(), dia))
                .thenReturn(List.of(bloque(dia, LocalTime.of(9, 0), LocalTime.of(12, 0))));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> disponibilidadService.validarReservaPermitida(profesional.getId(), 1L, inicio, fin)
        );

        assertTrue(ex.getMessage().contains("fuera de la disponibilidad"));
    }

    private Usuario profesional(Long id, Long tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSlug("tenant");
        tenant.setTimezone("America/Argentina/Buenos_Aires");

        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Profesional");
        usuario.setRole(Role.STAFF);
        usuario.setEnabled(true);
        usuario.setTenant(tenant);
        return usuario;
    }

    private Servicio servicio(Long id, String nombre, int duracionMinutos, int precio) {
        Servicio servicio = new Servicio();
        servicio.setId(id);
        servicio.setNombre(nombre);
        servicio.setActivo(true);
        servicio.setDuracionMinutos(duracionMinutos);
        servicio.setPrecio(BigDecimal.valueOf(precio));
        return servicio;
    }

    private DisponibilidadProfesional bloque(DiaSemana dia, LocalTime horaInicio, LocalTime horaFin) {
        DisponibilidadProfesional disponibilidad = new DisponibilidadProfesional();
        disponibilidad.setProfesional(profesional);
        disponibilidad.setDia(dia);
        disponibilidad.setHoraInicio(horaInicio);
        disponibilidad.setHoraFin(horaFin);
        disponibilidad.setActivo(true);
        return disponibilidad;
    }

    private Turno turno(LocalDateTime inicio, LocalDateTime fin) {
        Turno turno = new Turno();
        turno.setFechaHoraInicio(tenantDateTimeMapper.toInstant(inicio, profesional.getTenant()));
        turno.setFechaHoraFin(tenantDateTimeMapper.toInstant(fin, profesional.getTenant()));
        return turno;
    }
}
