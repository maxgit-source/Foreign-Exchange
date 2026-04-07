package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.NotificacionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificacionLogRepository extends JpaRepository<NotificacionLog, Long> {

    List<NotificacionLog> findByTurnoIdOrderByCreatedAtDesc(Long turnoId);

    @Query("""
        SELECT n FROM NotificacionLog n
        WHERE n.estado = 'ERROR'
        AND n.createdAt >= :desde
        ORDER BY n.createdAt DESC
        """)
    List<NotificacionLog> findErroresRecientes(@Param("desde") LocalDateTime desde);

    long countByEstado(String estado);
}
