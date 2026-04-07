package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.Seccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeccionRepository extends JpaRepository<Seccion, Long> {

    List<Seccion> findByPaginaIdOrderByOrdenAsc(Long paginaId);

    @Modifying
    @Query("DELETE FROM Seccion s WHERE s.pagina.id = :paginaId")
    void deleteByPaginaId(@Param("paginaId") Long paginaId);
}
