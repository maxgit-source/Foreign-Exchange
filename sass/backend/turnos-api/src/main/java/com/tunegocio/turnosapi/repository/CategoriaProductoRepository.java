package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.CategoriaProducto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaProductoRepository extends JpaRepository<CategoriaProducto, Long> {

    List<CategoriaProducto> findByActivoTrueOrderByOrdenAscNombreAsc();

    List<CategoriaProducto> findByOrderByOrdenAscNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
