package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.Turno;
import com.tunegocio.turnosapi.entity.TurnoStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TurnoRepository extends JpaRepository<Turno, Long>, JpaSpecificationExecutor<Turno> {

    @Override
    @EntityGraph(attributePaths = {"cliente", "profesional", "servicios", "turnoPadre"})
    Optional<Turno> findById(Long id);

    @EntityGraph(attributePaths = {"cliente", "profesional", "servicios", "turnoPadre"})
    List<Turno> findByCliente_IdOrderByFechaHoraInicioDesc(Long clienteId);

    @Query("""
        SELECT COUNT(t) > 0 FROM Turno t
        WHERE t.profesional.id = :profesionalId
          AND t.estado        <> :estadoExcluido
          AND t.fechaHoraInicio < :fin
          AND t.fechaHoraFin   > :inicio
        """)
    boolean existeSolapamiento(
            @Param("profesionalId") Long profesionalId,
            @Param("inicio") Instant inicio,
            @Param("fin") Instant fin,
            @Param("estadoExcluido") TurnoStatus estadoExcluido
    );

    @Query("""
        SELECT COUNT(t) > 0 FROM Turno t
        WHERE t.profesional.id = :profesionalId
          AND t.id            <> :turnoId
          AND t.estado        <> :estadoExcluido
          AND t.fechaHoraInicio < :fin
          AND t.fechaHoraFin   > :inicio
        """)
    boolean existeSolapamientoExcluyendoTurno(
            @Param("profesionalId") Long profesionalId,
            @Param("turnoId") Long turnoId,
            @Param("inicio") Instant inicio,
            @Param("fin") Instant fin,
            @Param("estadoExcluido") TurnoStatus estadoExcluido
    );

    @EntityGraph(attributePaths = {"cliente", "profesional", "servicios", "turnoPadre"})
    @Query("""
        SELECT t FROM Turno t
        WHERE t.profesional.id  = :profesionalId
          AND t.estado         <> :estadoExcluido
          AND t.fechaHoraInicio >= :inicio
          AND t.fechaHoraInicio <  :fin
        ORDER BY t.fechaHoraInicio ASC
        """)
    List<Turno> findTurnosActivosByProfesionalYRango(
            @Param("profesionalId") Long profesionalId,
            @Param("inicio") Instant inicio,
            @Param("fin") Instant fin,
            @Param("estadoExcluido") TurnoStatus estadoExcluido
    );

    @EntityGraph(attributePaths = {"cliente", "profesional", "servicios", "turnoPadre"})
    @Query("""
        SELECT t FROM Turno t
        WHERE t.fechaHoraInicio BETWEEN :desde AND :hasta
          AND t.estado IN :estados
          AND t.recordatorio24hEnviado = false
        ORDER BY t.fechaHoraInicio ASC
        """)
    List<Turno> findPendientesDeRecordatorio(
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta,
            @Param("estados") Collection<TurnoStatus> estados
    );

    long countByFechaHoraInicioGreaterThanEqualAndFechaHoraInicioLessThan(Instant desde, Instant hasta);

    // ─── Reportes ─────────────────────────────────────────────────────────────

    /**
     * Cuenta turnos agrupados por estado en un período.
     */
    @Query("""
        SELECT t.estado, COUNT(t)
        FROM Turno t
        WHERE t.fechaHoraInicio >= :desde AND t.fechaHoraInicio <= :hasta
        GROUP BY t.estado
        """)
    List<Object[]> countPorEstado(
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta
    );

    /**
     * Suma de ingresos (precio de servicios de turnos COMPLETADO) agrupada por día.
     * Usa native query para date trunc compatible con TIMESTAMPTZ.
     */
    @Query(nativeQuery = true, value = """
        SELECT DATE(fecha_hora_inicio AT TIME ZONE 'UTC') AS dia,
               COALESCE(SUM(s.precio), 0)                AS total
        FROM   turnos t
        JOIN   turno_servicios ts ON ts.turno_id = t.id
        JOIN   servicios       s  ON s.id        = ts.servicio_id
        WHERE  t.estado = 'COMPLETADO'
        AND    t.fecha_hora_inicio >= :desde
        AND    t.fecha_hora_inicio <= :hasta
        GROUP  BY dia
        ORDER  BY dia
        """)
    List<Object[]> ingresosPorDia(
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta
    );

    /**
     * Total de ingresos en un período (turnos COMPLETADO).
     */
    @Query(nativeQuery = true, value = """
        SELECT COALESCE(SUM(s.precio), 0)
        FROM   turnos t
        JOIN   turno_servicios ts ON ts.turno_id = t.id
        JOIN   servicios       s  ON s.id        = ts.servicio_id
        WHERE  t.estado = 'COMPLETADO'
        AND    t.fecha_hora_inicio >= :desde
        AND    t.fecha_hora_inicio <= :hasta
        """)
    java.math.BigDecimal sumIngresosPeriodo(
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta
    );

    /**
     * Top clientes por cantidad de turnos completados o confirmados.
     */
    @EntityGraph(attributePaths = {"cliente"})
    @Query("""
        SELECT t.cliente, COUNT(t) AS total
        FROM   Turno t
        WHERE  t.estado IN ('COMPLETADO', 'CONFIRMADO')
        GROUP  BY t.cliente
        ORDER  BY total DESC
        """)
    List<Object[]> topClientes(Pageable pageable);

    /**
     * Horas pico por cantidad de turnos no cancelados.
     */
    @Query(nativeQuery = true, value = """
        SELECT EXTRACT(HOUR FROM fecha_hora_inicio AT TIME ZONE 'UTC')::int AS hora,
               COUNT(*) AS cantidad
        FROM   turnos
        WHERE  estado <> 'CANCELADO'
        AND    fecha_hora_inicio >= :desde
        GROUP  BY hora
        ORDER  BY cantidad DESC
        """)
    List<Object[]> horasPico(@Param("desde") Instant desde);

    /**
     * Popularidad de servicios por cantidad de turnos.
     */
    @Query(nativeQuery = true, value = """
        SELECT s.id, s.nombre, COUNT(ts.turno_id) AS cantidad
        FROM   servicios s
        LEFT JOIN turno_servicios ts ON ts.servicio_id = s.id
        LEFT JOIN turnos t ON t.id = ts.turno_id AND t.estado <> 'CANCELADO'
        WHERE  s.activo = true
        GROUP  BY s.id, s.nombre
        ORDER  BY cantidad DESC
        """)
    List<Object[]> popularidadServicios(Pageable pageable);

    /**
     * Turnos del día actual (hoy, para un rango dado).
     */
    @EntityGraph(attributePaths = {"cliente", "profesional", "servicios"})
    @Query("""
        SELECT t FROM Turno t
        WHERE t.fechaHoraInicio >= :inicio
          AND t.fechaHoraInicio <  :fin
          AND t.estado NOT IN ('CANCELADO')
        ORDER BY t.fechaHoraInicio ASC
        """)
    List<Turno> findTurnosDelDia(
            @Param("inicio") Instant inicio,
            @Param("fin") Instant fin
    );

    /**
     * Clientes nuevos (creación de su primer turno) en un período.
     */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(DISTINCT cliente_id)
        FROM   turnos
        WHERE  created_at >= :desde AND created_at <= :hasta
        AND    cliente_id NOT IN (
            SELECT DISTINCT cliente_id FROM turnos WHERE created_at < :desde
        )
        """)
    long countClientesNuevos(
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta
    );
}
