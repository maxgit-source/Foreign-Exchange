package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.AdminTenantResponseDTO;
import com.tunegocio.turnosapi.dto.CambiarPlanRequestDTO;
import com.tunegocio.turnosapi.dto.SuscripcionResponseDTO;
import com.tunegocio.turnosapi.entity.HistorialPlan;
import com.tunegocio.turnosapi.entity.Plan;
import com.tunegocio.turnosapi.entity.Suscripcion;
import com.tunegocio.turnosapi.entity.SuscripcionEstado;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.HistorialPlanRepository;
import com.tunegocio.turnosapi.repository.SuscripcionRepository;
import com.tunegocio.turnosapi.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SuscripcionService {

    private final TenantRepository tenantRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final HistorialPlanRepository historialPlanRepository;
    private final AuditService auditService;

    @Transactional
    public void asegurarSuscripcionInicial(Tenant tenant) {
        suscripcionRepository.findByTenant_Id(tenant.getId())
                .orElseGet(() -> {
                    Suscripcion suscripcion = new Suscripcion();
                    suscripcion.setTenant(tenant);
                    suscripcion.setPlan(tenant.getPlan());
                    suscripcion.setEstado(SuscripcionEstado.ACTIVA);
                    suscripcion.setFechaInicio(LocalDate.now());
                    Suscripcion guardada = suscripcionRepository.save(suscripcion);
                    registrarHistorial(tenant, null, tenant.getPlan(), "REGISTRO");
                    return guardada;
                });
    }

    @Transactional(readOnly = true)
    public Page<AdminTenantResponseDTO> listarTenants(Pageable pageable) {
        Page<Tenant> tenants = tenantRepository.findAll(pageable);
        Map<Long, Suscripcion> suscripciones = suscripcionRepository.findByTenant_IdIn(
                        tenants.getContent().stream().map(Tenant::getId).toList()
                ).stream()
                .collect(Collectors.toMap(s -> s.getTenant().getId(), Function.identity()));

        return tenants.map(tenant -> {
            Suscripcion suscripcion = suscripciones.get(tenant.getId());
            return AdminTenantResponseDTO.builder()
                    .tenantId(tenant.getId())
                    .nombre(tenant.getNombre())
                    .slug(tenant.getSlug())
                    .email(tenant.getEmail())
                    .telefono(tenant.getTelefono())
                    .activo(tenant.isActivo())
                    .plan(tenant.getPlan().name())
                    .suscripcionEstado(suscripcion != null ? suscripcion.getEstado().name() : null)
                    .fechaInicio(suscripcion != null ? suscripcion.getFechaInicio() : null)
                    .fechaVencimiento(suscripcion != null ? suscripcion.getFechaVencimiento() : null)
                    .build();
        });
    }

    @Transactional(readOnly = true)
    public SuscripcionResponseDTO obtenerPorTenantId(Long tenantId) {
        return toDto(buscarSuscripcion(tenantId));
    }

    @Transactional
    public SuscripcionResponseDTO cambiarPlan(Long tenantId, CambiarPlanRequestDTO dto, Usuario actor) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
        Suscripcion suscripcion = suscripcionRepository.findByTenant_Id(tenantId)
                .orElseGet(() -> {
                    Suscripcion nueva = new Suscripcion();
                    nueva.setTenant(tenant);
                    nueva.setFechaInicio(LocalDate.now());
                    return nueva;
                });

        Plan planAnterior = tenant.getPlan();
        tenant.setPlan(dto.getPlan());
        tenantRepository.save(tenant);

        suscripcion.setTenant(tenant);
        suscripcion.setPlan(dto.getPlan());
        suscripcion.setEstado(dto.getEstado() != null ? dto.getEstado() : SuscripcionEstado.ACTIVA);
        if (suscripcion.getFechaInicio() == null) {
            suscripcion.setFechaInicio(LocalDate.now());
        }
        suscripcion.setFechaVencimiento(dto.getFechaVencimiento());
        suscripcion.setUpdatedAt(java.time.LocalDateTime.now());

        Suscripcion guardada = suscripcionRepository.save(suscripcion);
        registrarHistorial(tenant, planAnterior, dto.getPlan(), dto.getMotivo() != null ? dto.getMotivo() : "ADMIN");
        auditService.log(
                "UPDATE",
                "Suscripcion",
                guardada.getId(),
                tenant,
                actor,
                Map.of("planAnterior", planAnterior != null ? planAnterior.name() : null, "planNuevo", dto.getPlan().name())
        );
        return toDto(guardada);
    }

    private Suscripcion buscarSuscripcion(Long tenantId) {
        return suscripcionRepository.findByTenant_Id(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Suscripcion", "tenantId", tenantId));
    }

    private void registrarHistorial(Tenant tenant, Plan anterior, Plan nuevo, String motivo) {
        HistorialPlan historial = new HistorialPlan();
        historial.setTenant(tenant);
        historial.setPlanAnterior(anterior);
        historial.setPlanNuevo(nuevo);
        historial.setMotivo(motivo);
        historialPlanRepository.save(historial);
    }

    private SuscripcionResponseDTO toDto(Suscripcion suscripcion) {
        return SuscripcionResponseDTO.builder()
                .id(suscripcion.getId())
                .tenantId(suscripcion.getTenant().getId())
                .tenantNombre(suscripcion.getTenant().getNombre())
                .tenantSlug(suscripcion.getTenant().getSlug())
                .plan(suscripcion.getPlan().name())
                .estado(suscripcion.getEstado().name())
                .fechaInicio(suscripcion.getFechaInicio())
                .fechaVencimiento(suscripcion.getFechaVencimiento())
                .stripeSubscriptionId(suscripcion.getStripeSubscriptionId())
                .mercadopagoSubscriptionId(suscripcion.getMercadopagoSubscriptionId())
                .createdAt(suscripcion.getCreatedAt())
                .updatedAt(suscripcion.getUpdatedAt())
                .build();
    }
}
