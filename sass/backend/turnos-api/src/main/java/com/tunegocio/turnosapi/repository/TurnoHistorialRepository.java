package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.TurnoHistorial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TurnoHistorialRepository extends JpaRepository<TurnoHistorial, Long> {

    List<TurnoHistorial> findByTurno_IdOrderByCreatedAtDesc(Long turnoId);
}
