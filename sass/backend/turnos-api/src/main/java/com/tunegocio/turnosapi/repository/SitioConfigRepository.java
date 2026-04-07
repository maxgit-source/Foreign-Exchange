package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.SitioConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SitioConfigRepository extends JpaRepository<SitioConfig, Long> {

    /** Retorna la primera (y única) fila de configuración del sitio del tenant actual */
    Optional<SitioConfig> findFirstByOrderByIdAsc();
}
