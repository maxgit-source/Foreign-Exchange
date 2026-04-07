package com.tunegocio.turnosapi.dto;

import java.util.Map;

public record PlataformaMetricasDTO(
        long              totalTenants,
        long              tenantsActivos,
        Map<String, Long> tenantsPorPlan,
        long              totalUsuarios,
        long              totalStaff
) {}
