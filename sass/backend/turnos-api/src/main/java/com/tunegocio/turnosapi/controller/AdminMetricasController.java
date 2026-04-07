package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.PlataformaMetricasDTO;
import com.tunegocio.turnosapi.service.AdminMetricasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Métricas", description = "KPIs globales de la plataforma TuNegocio")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminMetricasController {

    private final AdminMetricasService adminMetricasService;

    @Operation(summary = "Métricas globales de la plataforma (tenants, usuarios, planes)")
    @GetMapping("/metricas")
    public ResponseEntity<PlataformaMetricasDTO> metricas() {
        return ResponseEntity.ok(adminMetricasService.metricas());
    }
}
