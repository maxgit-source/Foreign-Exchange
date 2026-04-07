package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.MediaFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {

    Page<MediaFile> findByTipoOrderByCreatedAtDesc(String tipo, Pageable pageable);

    Page<MediaFile> findByCarpetaOrderByCreatedAtDesc(String carpeta, Pageable pageable);

    Page<MediaFile> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<String> findDistinctCarpetaBy();
}
