package com.tunegocio.turnosapi.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricasService {

    private final MeterRegistry registry;

    // ─── Turnos ──────────────────────────────────────────────────────────────

    public void registrarTurnoCreado(String tenantSlug, String plan) {
        registry.counter("tunegocio.turnos.creados",
                "tenant", tenantSlug,
                "plan",   plan
        ).increment();
    }

    public void registrarTurnoCancelado(String tenantSlug) {
        registry.counter("tunegocio.turnos.cancelados",
                "tenant", tenantSlug
        ).increment();
    }

    public void registrarTurnoCompletado(String tenantSlug) {
        registry.counter("tunegocio.turnos.completados",
                "tenant", tenantSlug
        ).increment();
    }

    public void registrarNoShow(String tenantSlug) {
        registry.counter("tunegocio.turnos.no_shows",
                "tenant", tenantSlug
        ).increment();
    }

    // ─── Auth ────────────────────────────────────────────────────────────────

    public void registrarLogin(boolean exitoso) {
        registry.counter("tunegocio.auth.logins",
                "resultado", exitoso ? "exitoso" : "fallido"
        ).increment();
    }

    // ─── Tenants ─────────────────────────────────────────────────────────────

    public void registrarTenantRegistrado(String plan) {
        registry.counter("tunegocio.tenants.registrados",
                "plan", plan
        ).increment();
    }

    // ─── Pagos ───────────────────────────────────────────────────────────────

    public void registrarPagoConfirmado(String gateway, String plan) {
        registry.counter("tunegocio.pagos.confirmados",
                "gateway", gateway,
                "plan",    plan
        ).increment();
    }

    public void registrarPagoFallido(String gateway) {
        registry.counter("tunegocio.pagos.fallidos",
                "gateway", gateway
        ).increment();
    }

    // ─── API Keys ────────────────────────────────────────────────────────────

    public void registrarUsoApiKey(String tenantSlug) {
        registry.counter("tunegocio.apikeys.uso",
                "tenant", tenantSlug
        ).increment();
    }

    // ─── Timer util ──────────────────────────────────────────────────────────

    public Timer.Sample iniciarTimer() {
        return Timer.start(registry);
    }

    public void detenerTimer(Timer.Sample sample, String operacion, String resultado) {
        sample.stop(registry.timer("tunegocio.operacion.duracion",
                "operacion", operacion,
                "resultado", resultado
        ));
    }
}
