package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.BlogEstado;
import com.tunegocio.turnosapi.entity.BlogPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    Optional<BlogPost> findBySlug(String slug);

    Optional<BlogPost> findBySlugAndEstado(String slug, BlogEstado estado);

    Page<BlogPost> findByEstadoOrderByPublicadoAtDesc(BlogEstado estado, Pageable pageable);

    Page<BlogPost> findByCategoriaIdAndEstadoOrderByPublicadoAtDesc(Long categoriaId, BlogEstado estado, Pageable pageable);

    Page<BlogPost> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsBySlug(String slug);

    @Modifying
    @Query("UPDATE BlogPost b SET b.vistas = b.vistas + 1 WHERE b.id = :id")
    void incrementarVistas(@Param("id") Long id);
}
