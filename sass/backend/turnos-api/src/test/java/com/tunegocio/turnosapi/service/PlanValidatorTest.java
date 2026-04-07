package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.entity.Plan;
import com.tunegocio.turnosapi.entity.PlanLimite;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.exception.BusinessException;
import com.tunegocio.turnosapi.repository.ClienteRepository;
import com.tunegocio.turnosapi.repository.PlanLimiteRepository;
import com.tunegocio.turnosapi.repository.ProductoRepository;
import com.tunegocio.turnosapi.repository.ServicioRepository;
import com.tunegocio.turnosapi.repository.TurnoRepository;
import com.tunegocio.turnosapi.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanValidatorTest {

    @Mock
    private PlanLimiteRepository planLimiteRepository;
    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ServicioRepository servicioRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private TenantDateTimeMapper tenantDateTimeMapper;

    private PlanValidator planValidator;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        planValidator = new PlanValidator(
                planLimiteRepository,
                turnoRepository,
                usuarioRepository,
                servicioRepository,
                clienteRepository,
                productoRepository,
                tenantDateTimeMapper
        );

        tenant = new Tenant();
        tenant.setId(1L);
        tenant.setNombre("Demo");
        tenant.setSlug("demo");
        tenant.setPlan(Plan.BASIC);
        tenant.setTimezone("America/Argentina/Buenos_Aires");
    }

    @Test
    void validarPuedeCrearTurno_debeFallarCuandoAlcanzaElLimiteMensual() {
        when(planLimiteRepository.findById(Plan.BASIC)).thenReturn(Optional.of(plan(2, true, 10)));
        when(tenantDateTimeMapper.zoneId(tenant)).thenReturn(ZoneId.of("America/Argentina/Buenos_Aires"));
        when(turnoRepository.countByFechaHoraInicioGreaterThanEqualAndFechaHoraInicioLessThan(any(), any())).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> planValidator.validarPuedeCrearTurno(tenant));

        assertTrue(ex.getMessage().contains("turnos mensuales"));
    }

    @Test
    void validarPuedeCrearProducto_debeFallarSiElPlanNoIncluyeEcommerce() {
        when(planLimiteRepository.findById(Plan.BASIC)).thenReturn(Optional.of(plan(100, false, 0)));

        BusinessException ex = assertThrows(BusinessException.class, () -> planValidator.validarPuedeCrearProducto(tenant));

        assertTrue(ex.getMessage().contains("no incluye e-commerce"));
        verify(productoRepository, never()).countByActivoTrue();
    }

    @Test
    void validarPuedeCrearProfesional_debePermitirPlanesIlimitadosSinConsultarConteo() {
        PlanLimite limite = plan(100, true, 100);
        limite.setMaxProfesionales(-1);
        when(planLimiteRepository.findById(Plan.BASIC)).thenReturn(Optional.of(limite));

        assertDoesNotThrow(() -> planValidator.validarPuedeCrearProfesional(tenant));

        verify(usuarioRepository, never()).countByTenant_IdAndRoleInAndEnabledTrue(eq(tenant.getId()), any());
    }

    private PlanLimite plan(int maxTurnosMes, boolean ecommerce, int maxProductos) {
        PlanLimite limite = new PlanLimite();
        limite.setPlan(Plan.BASIC);
        limite.setMaxProfesionales(3);
        limite.setMaxServicios(20);
        limite.setMaxTurnosMes(maxTurnosMes);
        limite.setMaxClientes(500);
        limite.setMaxProductos(maxProductos);
        limite.setTieneEcommerce(ecommerce);
        limite.setTieneReportes(false);
        limite.setTieneApiPublica(true);
        limite.setTieneWhatsapp(false);
        return limite;
    }
}
