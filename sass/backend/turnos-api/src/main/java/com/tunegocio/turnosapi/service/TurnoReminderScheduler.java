package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.config.TenantContext;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Turno;
import com.tunegocio.turnosapi.entity.TurnoStatus;
import com.tunegocio.turnosapi.repository.TenantRepository;
import com.tunegocio.turnosapi.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TurnoReminderScheduler {

    private final TenantRepository tenantRepository;
    private final TurnoRepository turnoRepository;
    private final NotificacionService notificacionService;
    private final TenantDateTimeMapper tenantDateTimeMapper;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(cron = "${app.reminders.cron:0 0 * * * *}")
    public void enviarRecordatorios() {
        List<Tenant> tenants = tenantRepository.findAll();
        for (Tenant tenant : tenants) {
            if (!tenant.isActivo()) {
                continue;
            }

            LocalDateTime ahoraLocal = LocalDateTime.now(tenantDateTimeMapper.zoneId(tenant));
            Instant desde = tenantDateTimeMapper.toInstant(ahoraLocal.plusHours(23), tenant);
            Instant hasta = tenantDateTimeMapper.toInstant(ahoraLocal.plusHours(25), tenant);

            try (TenantContext.Scope ignored = TenantContext.openTenantSlug(tenant.getSlug())) {
                transactionTemplate.executeWithoutResult(status -> procesarTenant(desde, hasta));
            } catch (Exception ex) {
                log.warn("No se pudieron enviar recordatorios para tenant '{}': {}", tenant.getSlug(), ex.getMessage());
            }
        }

        log.debug("Scheduler de recordatorios ejecutado");
    }

    private void procesarTenant(Instant desde, Instant hasta) {
        List<Turno> turnos = turnoRepository.findPendientesDeRecordatorio(
                desde,
                hasta,
                List.of(TurnoStatus.PENDIENTE, TurnoStatus.CONFIRMADO)
        );

        for (Turno turno : turnos) {
            notificacionService.enviarRecordatorio(turno);
            turno.setRecordatorio24hEnviado(true);
        }
    }
}
