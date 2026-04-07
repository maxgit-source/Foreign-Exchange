package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.Plantilla;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlantillaRepository extends JpaRepository<Plantilla, Long> {

    List<Plantilla> findByActivaTrueOrderByOrdenAsc();

    List<Plantilla> findByCategoriaAndActivaTrueOrderByOrdenAsc(String categoria);

    Optional<Plantilla> findByCodigo(String codigo);
}
