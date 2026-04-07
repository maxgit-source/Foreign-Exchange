package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.DiaSemana;
import com.tunegocio.turnosapi.entity.DisponibilidadProfesional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisponibilidadRepository extends JpaRepository<DisponibilidadProfesional, Long> {

    List<DisponibilidadProfesional> findByProfesional_IdOrderByDiaAscHoraInicioAsc(Long profesionalId);

    List<DisponibilidadProfesional> findByProfesional_IdAndDiaAndActivoTrueOrderByHoraInicioAsc(
            Long profesionalId, DiaSemana dia);

    @Modifying
    @Query("DELETE FROM DisponibilidadProfesional d WHERE d.profesional.id = :profesionalId")
    void deleteByProfesionalId(@Param("profesionalId") Long profesionalId);
}
