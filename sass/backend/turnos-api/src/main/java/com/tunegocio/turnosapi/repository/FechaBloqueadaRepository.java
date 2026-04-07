package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.FechaBloqueada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FechaBloqueadaRepository extends JpaRepository<FechaBloqueada, Long> {

    @Query("""
        SELECT f FROM FechaBloqueada f
        LEFT JOIN FETCH f.profesional
        WHERE f.profesional.id = :profesionalId OR f.profesional IS NULL
        ORDER BY f.fechaInicio ASC, f.fechaFin ASC
        """)
    List<FechaBloqueada> findVisiblesByProfesionalId(@Param("profesionalId") Long profesionalId);

    @Query("""
        SELECT COUNT(f) > 0 FROM FechaBloqueada f
        WHERE (f.profesional.id = :profesionalId OR f.profesional IS NULL)
          AND f.fechaInicio <= :fecha
          AND f.fechaFin >= :fecha
        """)
    boolean existsBloqueoVisibleEnFecha(@Param("profesionalId") Long profesionalId,
                                        @Param("fecha") LocalDate fecha);
}
