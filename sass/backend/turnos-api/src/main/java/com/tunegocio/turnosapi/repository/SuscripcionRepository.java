package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    Optional<Suscripcion> findByTenant_Id(Long tenantId);

    List<Suscripcion> findByTenant_IdIn(Collection<Long> tenantIds);
}
