package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.BlogCategoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlogCategoriaRepository extends JpaRepository<BlogCategoria, Long> {

    Optional<BlogCategoria> findBySlug(String slug);

    List<BlogCategoria> findAllByOrderByNombreAsc();

    boolean existsBySlug(String slug);
}
