package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.*;
import com.tunegocio.turnosapi.entity.Cliente;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.TurnoStatus;
import com.tunegocio.turnosapi.repository.ClienteRepository;
import com.tunegocio.turnosapi.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportesService {

    private final TurnoRepository     turnoRepository;
    private final ClienteRepository   clienteRepository;
    private final TurnoMapper         turnoMapper;
    private final TenantDateTimeMapper tenantDateTimeMapper;

    // ─── Dashboard ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DashboardDTO dashboard(int dias, Tenant tenant) {
        ZoneId zone    = tenantDateTimeMapper.zoneId(tenant);
        Instant ahora  = Instant.now();
        LocalDate hoy  = LocalDate.now(zone);

        Instant inicioHoy     = hoy.atStartOfDay(zone).toInstant();
        Instant finHoy        = hoy.plusDays(1).atStartOfDay(zone).toInstant();
        Instant inicioSemana  = hoy.minusDays(6).atStartOfDay(zone).toInstant();
        Instant inicioMes     = hoy.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        Instant inicioMesAnt  = hoy.withDayOfMonth(1).minusMonths(1).atStartOfDay(zone).toInstant();
        Instant finMesAnt     = hoy.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        Instant inicioPeriodo = hoy.minusDays(dias - 1).atStartOfDay(zone).toInstant();

        // Estadísticas del mes actual
        Map<String, Long> porEstado = contarPorEstado(inicioMes, ahora);
        long totalMes       = porEstado.values().stream().mapToLong(Long::longValue).sum();
        long completados    = porEstado.getOrDefault("COMPLETADO", 0L);
        long cancelados     = porEstado.getOrDefault("CANCELADO",  0L);
        long noShows        = porEstado.getOrDefault("NO_SHOW",    0L);

        // Conteos de turnos
        long turnosHoy     = countTurnos(inicioHoy, finHoy);
        long turnosSemana  = countTurnos(inicioSemana, ahora);
        long turnosMes     = totalMes;

        double tasaOcupacion   = totalMes > 0 ? (double)(completados) / totalMes * 100 : 0;
        double tasaCancelacion = totalMes > 0 ? (double) cancelados   / totalMes * 100 : 0;
        double tasaNoShow      = totalMes > 0 ? (double) noShows      / totalMes * 100 : 0;

        // Ingresos
        BigDecimal ingresosMes     = safeSum(turnoRepository.sumIngresosPeriodo(inicioMes, ahora));
        BigDecimal ingresosAnterior= safeSum(turnoRepository.sumIngresosPeriodo(inicioMesAnt, finMesAnt));
        double variacion = calcVariacion(ingresosMes, ingresosAnterior);

        // Clientes
        long clientesTotales = clienteRepository.countByActivoTrue();
        long clientesNuevos  = turnoRepository.countClientesNuevos(inicioPeriodo, ahora);

        // Servicios populares (top 5)
        List<ServicioKpiDTO> serviciosPopulares = popularidadServicios(5);

        // Horas pico (últimos 30 días)
        Instant hace30Dias = hoy.minusDays(30).atStartOfDay(zone).toInstant();
        List<HoraOcupacionDTO> horasPico = horasPico(hace30Dias);

        // Próximos turnos del día
        List<TurnoResponseDTO> proximosTurnos = turnoRepository
                .findTurnosDelDia(inicioHoy, finHoy)
                .stream()
                .limit(10)
                .map(t -> turnoMapper.toResponseDTO(t, tenant))
                .toList();

        return new DashboardDTO(
                turnosHoy, turnosSemana, turnosMes,
                redondear(tasaOcupacion), redondear(tasaNoShow), redondear(tasaCancelacion),
                ingresosMes, ingresosAnterior, redondear(variacion),
                clientesNuevos, clientesTotales,
                serviciosPopulares, horasPico,
                proximosTurnos
        );
    }

    // ─── Ingresos ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public IngresoReporteDTO ingresos(LocalDate desde, LocalDate hasta, Tenant tenant) {
        ZoneId zone       = tenantDateTimeMapper.zoneId(tenant);
        Instant iDesde    = desde.atStartOfDay(zone).toInstant();
        Instant iHasta    = hasta.plusDays(1).atStartOfDay(zone).toInstant();

        long duracionDias = hasta.toEpochDay() - desde.toEpochDay() + 1;
        Instant iAntDesde = desde.minusDays(duracionDias).atStartOfDay(zone).toInstant();
        Instant iAntHasta = iDesde;

        BigDecimal total         = safeSum(turnoRepository.sumIngresosPeriodo(iDesde, iHasta));
        BigDecimal totalAnterior = safeSum(turnoRepository.sumIngresosPeriodo(iAntDesde, iAntHasta));
        double variacion         = calcVariacion(total, totalAnterior);

        List<IngresoReporteDTO.IngresosPorDiaDTO> porDia = turnoRepository
                .ingresosPorDia(iDesde, iHasta)
                .stream()
                .map(row -> new IngresoReporteDTO.IngresosPorDiaDTO(
                        parseLocalDate(row[0]),
                        parseBigDecimal(row[1])
                ))
                .toList();

        return new IngresoReporteDTO(desde, hasta, total, totalAnterior, redondear(variacion), porDia);
    }

    // ─── Estadísticas turnos ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TurnoEstadisticasDTO estadisticasTurnos(int dias, Tenant tenant) {
        ZoneId zone       = tenantDateTimeMapper.zoneId(tenant);
        Instant ahora     = Instant.now();
        LocalDate hoy     = LocalDate.now(zone);

        Instant inicio    = hoy.minusDays(dias - 1).atStartOfDay(zone).toInstant();
        Instant inicioHoy = hoy.atStartOfDay(zone).toInstant();
        Instant fin7      = hoy.minusDays(6).atStartOfDay(zone).toInstant();
        Instant inicioMes = hoy.withDayOfMonth(1).atStartOfDay(zone).toInstant();

        Map<String, Long> porEstado = contarPorEstado(inicio, ahora);
        long total       = porEstado.values().stream().mapToLong(Long::longValue).sum();
        long completados = porEstado.getOrDefault("COMPLETADO", 0L);
        long cancelados  = porEstado.getOrDefault("CANCELADO",  0L);
        long noShows     = porEstado.getOrDefault("NO_SHOW",    0L);

        return new TurnoEstadisticasDTO(
                total,
                porEstado,
                total > 0 ? redondear((double) completados / total * 100) : 0,
                total > 0 ? redondear((double) cancelados  / total * 100) : 0,
                total > 0 ? redondear((double) noShows     / total * 100) : 0,
                countTurnos(inicioHoy, ahora),
                countTurnos(fin7, ahora),
                countTurnos(inicioMes, ahora)
        );
    }

    // ─── Top clientes ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ClienteKpiDTO> topClientes(int limit) {
        return turnoRepository.topClientes(PageRequest.of(0, limit))
                .stream()
                .map(row -> {
                    Cliente c     = (Cliente) row[0];
                    long cantidad = (Long) row[1];
                    return new ClienteKpiDTO(
                            c.getId(),
                            c.getNombreCompleto(),
                            c.getEmail(),
                            cantidad
                    );
                })
                .toList();
    }

    // ─── Popularidad servicios ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ServicioKpiDTO> popularidadServicios(int limit) {
        List<Object[]> rows = turnoRepository.popularidadServicios(PageRequest.of(0, limit));
        long total = rows.stream()
                .mapToLong(row -> ((Number) row[2]).longValue())
                .sum();

        return rows.stream()
                .map(row -> {
                    long cantidad = ((Number) row[2]).longValue();
                    double pct    = total > 0 ? redondear((double) cantidad / total * 100) : 0;
                    return new ServicioKpiDTO(
                            ((Number) row[0]).longValue(),
                            (String) row[1],
                            cantidad,
                            pct
                    );
                })
                .toList();
    }

    // ─── Horas pico ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<HoraOcupacionDTO> horasPico(Instant desde) {
        return turnoRepository.horasPico(desde)
                .stream()
                .map(row -> new HoraOcupacionDTO(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    // ─── Exportación ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TurnoResponseDTO> turnosParaExportar(LocalDate desde, LocalDate hasta, Tenant tenant) {
        ZoneId zone    = tenantDateTimeMapper.zoneId(tenant);
        Instant iDesde = desde.atStartOfDay(zone).toInstant();
        Instant iHasta = hasta.plusDays(1).atStartOfDay(zone).toInstant();

        return turnoRepository.findTurnosDelDia(iDesde, iHasta)
                .stream()
                .map(t -> turnoMapper.toResponseDTO(t, tenant))
                .toList();
    }

    // ─── Helpers privados ────────────────────────────────────────────────────

    private Map<String, Long> contarPorEstado(Instant desde, Instant hasta) {
        return turnoRepository.countPorEstado(desde, hasta)
                .stream()
                .collect(Collectors.toMap(
                        row -> ((TurnoStatus) row[0]).name(),
                        row -> (Long) row[1]
                ));
    }

    private long countTurnos(Instant desde, Instant hasta) {
        return turnoRepository
                .countByFechaHoraInicioGreaterThanEqualAndFechaHoraInicioLessThan(desde, hasta);
    }

    private BigDecimal safeSum(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private double calcVariacion(BigDecimal actual, BigDecimal anterior) {
        if (anterior == null || anterior.compareTo(BigDecimal.ZERO) == 0) {
            return actual != null && actual.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        }
        return actual.subtract(anterior)
                .divide(anterior, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private double redondear(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private LocalDate parseLocalDate(Object value) {
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(value.toString());
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
