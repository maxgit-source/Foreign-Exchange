package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.Pagina;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaginaRepository extends JpaRepository<Pagina, Long> {

    Optional<Pagina> findBySlug(String slug);

    Optional<Pagina> findByEsHomeTrue();

    List<Pagina> findByEnNavTrueOrderByOrdenNavAsc();

    List<Pagina> findByActivaTrueOrderByOrdenNavAsc();

    boolean existsBySlug(String slug);
}
