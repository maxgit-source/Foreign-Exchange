package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.entity.PlanLimite;
import com.tunegocio.turnosapi.entity.Role;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.exception.BusinessException;
import com.tunegocio.turnosapi.repository.ClienteRepository;
import com.tunegocio.turnosapi.repository.PlanLimiteRepository;
import com.tunegocio.turnosapi.repository.ProductoRepository;
import com.tunegocio.turnosapi.repository.ServicioRepository;
import com.tunegocio.turnosapi.repository.TurnoRepository;
import com.tunegocio.turnosapi.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanValidator {

    private final PlanLimiteRepository planLimiteRepository;
    private final TurnoRepository turnoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicioRepository servicioRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final TenantDateTimeMapper tenantDateTimeMapper;

    public void validarPuedeCrearTurno(Tenant tenant) {
        PlanLimite limites = limites(tenant);
        if (isUnlimited(limites.getMaxTurnosMes())) {
            return;
        }

        ZoneId zoneId = tenantDateTimeMapper.zoneId(tenant);
        LocalDate hoy = LocalDate.now(zoneId);
        long turnosMes = turnoRepository.countByFechaHoraInicioGreaterThanEqualAndFechaHoraInicioLessThan(
                hoy.withDayOfMonth(1).atStartOfDay(zoneId).toInstant(),
                hoy.withDayOfMonth(1).plusMonths(1).atStartOfDay(zoneId).toInstant()
        );

        if (turnosMes >= limites.getMaxTurnosMes()) {
            throw limiteExcedido("turnos mensuales", tenant);
        }
    }

    public void validarPuedeCrearProfesional(Tenant tenant) {
        PlanLimite limites = limites(tenant);
        if (isUnlimited(limites.getMaxProfesionales())) {
            return;
        }

        long profesionales = usuarioRepository.countByTenant_IdAndRoleInAndEnabledTrue(
                tenant.getId(),
                List.of(Role.OWNER, Role.STAFF)
        );
        if (profesionales >= limites.getMaxProfesionales()) {
            throw limiteExcedido("profesionales", tenant);
        }
    }

    public void validarPuedeCrearServicio(Tenant tenant) {
        PlanLimite limites = limites(tenant);
        if (isUnlimited(limites.getMaxServicios())) {
            return;
        }

        long servicios = servicioRepository.countByActivoTrue();
        if (servicios >= limites.getMaxServicios()) {
            throw limiteExcedido("servicios", tenant);
        }
    }

    public void validarPuedeCrearCliente(Tenant tenant) {
        PlanLimite limites = limites(tenant);
        if (isUnlimited(limites.getMaxClientes())) {
            return;
        }

        long clientes = clienteRepository.countByActivoTrue();
        if (clientes >= limites.getMaxClientes()) {
            throw limiteExcedido("clientes", tenant);
        }
    }

    public void validarPuedeCrearProducto(Tenant tenant) {
        validarPuedeUsarEcommerce(tenant);
        PlanLimite limites = limites(tenant);
        if (isUnlimited(limites.getMaxProductos())) {
            return;
        }

        long productos = productoRepository.countByActivoTrue();
        if (productos >= limites.getMaxProductos()) {
            throw limiteExcedido("productos", tenant);
        }
    }

    public void validarPuedeUsarEcommerce(Tenant tenant) {
        if (!limites(tenant).isTieneEcommerce()) {
            throw new BusinessException("El plan " + tenant.getPlan() + " no incluye e-commerce");
        }
    }

    public void validarPuedeVerReportes(Tenant tenant) {
        if (!limites(tenant).isTieneReportes()) {
            throw new BusinessException("El plan " + tenant.getPlan() + " no incluye reportes");
        }
    }

    public void validarPuedeUsarApiPublica(Tenant tenant) {
        if (!limites(tenant).isTieneApiPublica()) {
            throw new BusinessException("El plan " + tenant.getPlan() + " no incluye API publica");
        }
    }

    private PlanLimite limites(Tenant tenant) {
        return planLimiteRepository.findById(tenant.getPlan())
                .orElseThrow(() -> new IllegalStateException("No existen limites configurados para el plan " + tenant.getPlan()));
    }

    private boolean isUnlimited(Integer value) {
        return value != null && value < 0;
    }

    private BusinessException limiteExcedido(String recurso, Tenant tenant) {
        return new BusinessException(
                "Limite de " + recurso + " alcanzado para el plan " + tenant.getPlan() + ". Actualice la suscripcion para ampliar capacidad."
        );
    }
}
