package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByEmailIgnoreCase(String email);

    @Query("""
        SELECT c FROM Cliente c
        WHERE c.activo = true
          AND (:busqueda IS NULL OR :busqueda = ''
               OR LOWER(c.nombre)   LIKE LOWER(CONCAT('%', :busqueda, '%'))
               OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :busqueda, '%'))
               OR LOWER(c.email)    LIKE LOWER(CONCAT('%', :busqueda, '%'))
               OR c.telefono        LIKE CONCAT('%', :busqueda, '%'))
        ORDER BY c.nombre ASC, c.apellido ASC
        """)
    Page<Cliente> buscar(@Param("busqueda") String busqueda, Pageable pageable);

    long countByActivoTrue();
}
