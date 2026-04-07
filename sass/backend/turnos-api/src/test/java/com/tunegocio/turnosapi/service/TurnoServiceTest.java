package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.ReprogramarTurnoDTO;
import com.tunegocio.turnosapi.dto.TurnoRequestDTO;
import com.tunegocio.turnosapi.dto.TurnoResponseDTO;
import com.tunegocio.turnosapi.entity.Cliente;
import com.tunegocio.turnosapi.entity.Role;
import com.tunegocio.turnosapi.entity.Servicio;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Turno;
import com.tunegocio.turnosapi.entity.TurnoHistorial;
import com.tunegocio.turnosapi.entity.TurnoStatus;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.exception.BusinessException;
import com.tunegocio.turnosapi.exception.ConflictException;
import com.tunegocio.turnosapi.repository.TurnoHistorialRepository;
import com.tunegocio.turnosapi.repository.TurnoRepository;
import com.tunegocio.turnosapi.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TurnoServiceTest {

    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private TurnoHistorialRepository turnoHistorialRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ClienteService clienteService;
    @Mock
    private ServicioService servicioService;
    @Mock
    private DisponibilidadService disponibilidadService;
    @Mock
    private NotificacionService notificacionService;
    @Mock
    private PlanValidator planValidator;
    @Mock
    private AuditService auditService;

    private TenantDateTimeMapper tenantDateTimeMapper;
    private TurnoMapper turnoMapper;
    private TurnoService turnoService;

    private Usuario actor;
    private Usuario profesional;
    private Cliente cliente;
    private Servicio corte;
    private Servicio color;

    @BeforeEach
    void setUp() {
        tenantDateTimeMapper = new TenantDateTimeMapper();
        turnoMapper = new TurnoMapper(tenantDateTimeMapper);
        turnoService = new TurnoService(
                turnoRepository,
                turnoHistorialRepository,
                usuarioRepository,
                clienteService,
                servicioService,
                disponibilidadService,
                notificacionService,
                tenantDateTimeMapper,
                turnoMapper,
                planValidator,
                auditService
        );

        actor = owner(1L, 1L);
        profesional = staff(2L, 1L);
        cliente = cliente(10L);
        corte = servicio(20L, "Corte", 60, 15000);
        color = servicio(21L, "Color", 45, 22000);

    }

    @Test
    void crear_debeRechazarFechaPasada() {
        TurnoRequestDTO dto = turnoRequest(LocalDateTime.now().minusDays(1), List.of(corte.getId()), false, null);

        mockDependenciasBasicas(List.of(corte));

        BusinessException ex = assertThrows(BusinessException.class, () -> turnoService.crear(dto, actor));

        assertTrue(ex.getMessage().contains("pasado"));
        verify(turnoRepository, never()).save(any());
    }

    @Test
    void crear_debeRechazarSolapamiento() {
        LocalDateTime inicio = futuraFecha(2).withHour(10).withMinute(0);
        TurnoRequestDTO dto = turnoRequest(inicio, List.of(corte.getId()), false, null);

        mockDependenciasBasicas(List.of(corte));
        doNothing().when(disponibilidadService).validarReservaPermitida(anyLong(), anyLong(), any(), any());
        when(turnoRepository.existeSolapamiento(anyLong(), any(), any(), any())).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () -> turnoService.crear(dto, actor));

        assertTrue(ex.getMessage().contains("disponible"));
        verify(turnoRepository, never()).save(any());
    }

    @Test
    void crear_debeCalcularDuracionYPrecioTotalParaMultiplesServicios() {
        LocalDateTime inicio = futuraFecha(3).withHour(9).withMinute(30);
        TurnoRequestDTO dto = turnoRequest(inicio, List.of(corte.getId(), color.getId()), false, null);

        mockDependenciasBasicas(List.of(corte, color));
        doNothing().when(disponibilidadService).validarReservaPermitida(anyLong(), anyLong(), any(), any());
        when(turnoRepository.existeSolapamiento(anyLong(), any(), any(), any())).thenReturn(false);
        when(turnoHistorialRepository.save(any(TurnoHistorial.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(turnoRepository.save(any(Turno.class))).thenAnswer(invocation -> {
            Turno turno = invocation.getArgument(0);
            turno.setId(100L);
            turno.setCreatedAt(LocalDateTime.now());
            turno.setUpdatedAt(LocalDateTime.now());
            return turno;
        });

        TurnoResponseDTO response = turnoService.crear(dto, actor);

        ArgumentCaptor<Turno> captor = ArgumentCaptor.forClass(Turno.class);
        verify(turnoRepository).save(captor.capture());
        Turno guardado = captor.getValue();

        LocalDateTime finEsperado = inicio.plusMinutes(105);
        assertEquals(2, guardado.getServicios().size());
        assertEquals(tenantDateTimeMapper.toInstant(inicio, actor.getTenant()), guardado.getFechaHoraInicio());
        assertEquals(tenantDateTimeMapper.toInstant(finEsperado, actor.getTenant()), guardado.getFechaHoraFin());
        assertFalse(guardado.isRecordatorio24hEnviado());
        assertEquals(105, response.getDuracionTotalMinutos());
        assertEquals(0, BigDecimal.valueOf(37000).compareTo(response.getPrecioTotal()));
        assertEquals(finEsperado, response.getFechaHoraFin());
        verify(turnoHistorialRepository).save(any(TurnoHistorial.class));
        verify(notificacionService).enviarConfirmacionTurno(any(Turno.class));
    }

    @Test
    void crearRecurrente_debeCrearSerieCompletaYVincularPadre() {
        LocalDateTime inicio = futuraFecha(4).withHour(8).withMinute(0);
        TurnoRequestDTO dto = turnoRequest(inicio, List.of(corte.getId()), true, 2);

        mockDependenciasBasicas(List.of(corte));
        doNothing().when(disponibilidadService).validarReservaPermitida(anyLong(), anyLong(), any(), any());
        when(turnoRepository.existeSolapamiento(anyLong(), any(), any(), any())).thenReturn(false);
        when(turnoHistorialRepository.save(any(TurnoHistorial.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AtomicLong ids = new AtomicLong(200L);
        when(turnoRepository.save(any(Turno.class))).thenAnswer(invocation -> {
            Turno turno = invocation.getArgument(0);
            turno.setId(ids.getAndIncrement());
            return turno;
        });

        TurnoResponseDTO response = turnoService.crear(dto, actor);

        ArgumentCaptor<Turno> captor = ArgumentCaptor.forClass(Turno.class);
        verify(turnoRepository, times(3)).save(captor.capture());
        List<Turno> guardados = captor.getAllValues();

        Turno padre = guardados.get(0);
        Turno segunda = guardados.get(1);
        Turno tercera = guardados.get(2);

        assertNull(padre.getTurnoPadre());
        assertTrue(padre.isRecurrente());
        assertEquals(2, padre.getRecurrenciaSemanas());
        assertEquals(padre, segunda.getTurnoPadre());
        assertEquals(padre, tercera.getTurnoPadre());
        assertEquals(tenantDateTimeMapper.toInstant(inicio.plusWeeks(1), actor.getTenant()), segunda.getFechaHoraInicio());
        assertEquals(tenantDateTimeMapper.toInstant(inicio.plusWeeks(2), actor.getTenant()), tercera.getFechaHoraInicio());
        assertEquals(padre.getId(), response.getId());
        assertTrue(response.isRecurrente());
        assertEquals(2, response.getRecurrenciaSemanas());
        verify(turnoHistorialRepository, times(3)).save(any(TurnoHistorial.class));
        verify(notificacionService, times(3)).enviarConfirmacionTurno(any(Turno.class));
    }

    @Test
    void reprogramar_debeActualizarFechasYResetearRecordatorio() {
        LocalDateTime inicioOriginal = futuraFecha(2).withHour(8).withMinute(0);
        LocalDateTime nuevoInicio = futuraFecha(3).withHour(11).withMinute(0);
        Turno turno = turnoExistente(203L, TurnoStatus.CONFIRMADO, inicioOriginal, List.of(corte, color));
        turno.setRecordatorio24hEnviado(true);

        ReprogramarTurnoDTO dto = new ReprogramarTurnoDTO();
        dto.setNuevaFechaHoraInicio(nuevoInicio);

        when(turnoRepository.findById(203L)).thenReturn(Optional.of(turno));
        when(turnoRepository.existeSolapamientoExcluyendoTurno(anyLong(), anyLong(), any(), any(), any())).thenReturn(false);
        when(turnoHistorialRepository.save(any(TurnoHistorial.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(turnoRepository.save(any(Turno.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TurnoResponseDTO response = turnoService.reprogramar(203L, dto, actor);

        LocalDateTime nuevoFin = nuevoInicio.plusMinutes(105);
        assertEquals(tenantDateTimeMapper.toInstant(nuevoInicio, actor.getTenant()), turno.getFechaHoraInicio());
        assertEquals(tenantDateTimeMapper.toInstant(nuevoFin, actor.getTenant()), turno.getFechaHoraFin());
        assertFalse(turno.isRecordatorio24hEnviado());
        assertEquals(nuevoInicio, response.getFechaHoraInicio());
        assertEquals(nuevoFin, response.getFechaHoraFin());
        verify(disponibilidadService).validarReservaPermitida(
                eq(profesional.getId()),
                eq(actor.getTenant().getId()),
                eq(nuevoInicio),
                eq(nuevoFin)
        );
        verify(notificacionService).enviarReprogramacion(turno);
    }

    private void mockDependenciasBasicas(List<Servicio> servicios) {
        when(clienteService.buscarPorId(cliente.getId())).thenReturn(cliente);
        when(servicioService.buscarPorIdsActivos(servicios.stream().map(Servicio::getId).toList())).thenReturn(servicios);
        when(usuarioRepository.findByIdAndTenant_IdAndRoleIn(
                eq(profesional.getId()),
                eq(actor.getTenant().getId()),
                eq(List.of(Role.OWNER, Role.STAFF))
        )).thenReturn(Optional.of(profesional));
    }

    private TurnoRequestDTO turnoRequest(LocalDateTime inicio, List<Long> servicioIds, boolean recurrente, Integer semanas) {
        TurnoRequestDTO dto = new TurnoRequestDTO();
        dto.setClienteId(cliente.getId());
        dto.setProfesionalId(profesional.getId());
        dto.setServicioIds(servicioIds);
        dto.setFechaHoraInicio(inicio);
        dto.setRecurrente(recurrente);
        dto.setSemanas(semanas);
        dto.setNotas("Nota");
        return dto;
    }

    private Turno turnoExistente(Long id, TurnoStatus estado, LocalDateTime inicio, List<Servicio> servicios) {
        Turno turno = new Turno();
        turno.setId(id);
        turno.setCliente(cliente);
        turno.setProfesional(profesional);
        turno.getServicios().addAll(servicios);
        turno.setFechaHoraInicio(tenantDateTimeMapper.toInstant(inicio, actor.getTenant()));
        turno.setFechaHoraFin(tenantDateTimeMapper.toInstant(
                inicio.plusMinutes(servicios.stream().mapToInt(Servicio::getDuracionMinutos).sum()),
                actor.getTenant()
        ));
        turno.setEstado(estado);
        turno.setCreatedAt(LocalDateTime.now());
        turno.setUpdatedAt(LocalDateTime.now());
        return turno;
    }

    private LocalDateTime futuraFecha(int dias) {
        return LocalDateTime.now(tenantDateTimeMapper.zoneId(actor.getTenant())).plusDays(dias).withSecond(0).withNano(0);
    }

    private Usuario owner(Long id, Long tenantId) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Owner");
        usuario.setEmail("owner@test.com");
        usuario.setRole(Role.OWNER);
        usuario.setTenant(tenant(tenantId));
        usuario.setEnabled(true);
        return usuario;
    }

    private Usuario staff(Long id, Long tenantId) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Profesional");
        usuario.setEmail("staff@test.com");
        usuario.setRole(Role.STAFF);
        usuario.setTenant(tenant(tenantId));
        usuario.setEnabled(true);
        return usuario;
    }

    private Tenant tenant(Long id) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setNombre("Tenant");
        tenant.setSlug("tenant");
        tenant.setTimezone("America/Argentina/Buenos_Aires");
        return tenant;
    }

    private Cliente cliente(Long id) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setNombre("Ana");
        cliente.setApellido("Cliente");
        cliente.setEmail("ana@test.com");
        return cliente;
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
}
