package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.Producto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {

    @Override
    @EntityGraph(attributePaths = {"categoria"})
    Optional<Producto> findById(Long id);

    @EntityGraph(attributePaths = {"categoria"})
    Optional<Producto> findByIdAndActivoTrue(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p LEFT JOIN FETCH p.categoria WHERE p.id = :id")
    Optional<Producto> findLockedById(@Param("id") Long id);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    long countByActivoTrue();
}
