package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ServicioRepository extends JpaRepository<Servicio, Long> {

    List<Servicio> findByActivoTrueOrderByNombreAsc();

    List<Servicio> findByActivoTrueAndCategoria_IdOrderByNombreAsc(Long categoriaId);

    List<Servicio> findByOrderByNombreAsc();

    List<Servicio> findByCategoria_IdOrderByNombreAsc(Long categoriaId);

    List<Servicio> findByIdIn(Collection<Long> ids);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    long countByActivoTrue();
}
