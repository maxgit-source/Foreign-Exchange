package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.PlataformaMetricasDTO;
import com.tunegocio.turnosapi.entity.Plan;
import com.tunegocio.turnosapi.entity.Role;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.repository.TenantRepository;
import com.tunegocio.turnosapi.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMetricasService {

    private final TenantRepository  tenantRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public PlataformaMetricasDTO metricas() {
        List<Tenant> tenants = tenantRepository.findAll();

        long totalTenants  = tenants.size();
        long tenantsActivos = tenants.stream().filter(Tenant::isActivo).count();

        Map<String, Long> porPlan = Arrays.stream(Plan.values())
                .collect(Collectors.toMap(
                        Plan::name,
                        plan -> tenants.stream()
                                .filter(t -> t.getPlan() == plan)
                                .count()
                ));

        long totalUsuarios = usuarioRepository.count();
        long totalStaff    = usuarioRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.STAFF)
                .count();

        return new PlataformaMetricasDTO(
                totalTenants,
                tenantsActivos,
                porPlan,
                totalUsuarios,
                totalStaff
        );
    }
}
