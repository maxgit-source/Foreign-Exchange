package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.Pedido;
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
public interface PedidoRepository extends JpaRepository<Pedido, Long>, JpaSpecificationExecutor<Pedido> {

    @Override
    @EntityGraph(attributePaths = {"cliente", "items", "items.producto", "items.turno", "items.turno.profesional", "items.turno.cliente", "items.turno.servicios"})
    Optional<Pedido> findById(Long id);

    @EntityGraph(attributePaths = {"cliente", "items", "items.producto", "items.turno", "items.turno.profesional", "items.turno.cliente", "items.turno.servicios"})
    Optional<Pedido> findByPaymentIntentId(String paymentIntentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT DISTINCT p FROM Pedido p
            LEFT JOIN FETCH p.cliente
            LEFT JOIN FETCH p.items i
            LEFT JOIN FETCH i.producto
            LEFT JOIN FETCH i.turno t
            LEFT JOIN FETCH t.profesional
            LEFT JOIN FETCH t.cliente
            LEFT JOIN FETCH t.servicios
            WHERE p.id = :id
            """)
    Optional<Pedido> findLockedById(@Param("id") Long id);
}
