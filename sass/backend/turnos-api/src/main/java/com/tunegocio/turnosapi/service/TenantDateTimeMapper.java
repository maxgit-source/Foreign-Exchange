package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.entity.Tenant;
import org.springframework.stereotype.Component;

import java.time.*;

@Component
public class TenantDateTimeMapper {

    public ZoneId zoneId(Tenant tenant) {
        return ZoneId.of(tenant.getTimezone());
    }

    public Instant toInstant(LocalDateTime fechaHoraLocal, Tenant tenant) {
        return fechaHoraLocal.atZone(zoneId(tenant)).toInstant();
    }

    public LocalDateTime toLocalDateTime(Instant instant, Tenant tenant) {
        return LocalDateTime.ofInstant(instant, zoneId(tenant));
    }

    public Instant startOfDay(LocalDate fecha, Tenant tenant) {
        return fecha.atStartOfDay(zoneId(tenant)).toInstant();
    }

    public Instant startOfNextDay(LocalDate fecha, Tenant tenant) {
        return fecha.plusDays(1).atStartOfDay(zoneId(tenant)).toInstant();
    }

    public Instant slotToInstant(LocalDate fecha, LocalTime hora, Tenant tenant) {
        return LocalDateTime.of(fecha, hora).atZone(zoneId(tenant)).toInstant();
    }
}
