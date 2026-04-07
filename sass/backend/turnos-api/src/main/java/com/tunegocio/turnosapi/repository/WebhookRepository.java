package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    List<Webhook> findByTenant_IdAndActivoTrueOrderByCreatedAtDesc(Long tenantId);

    List<Webhook> findByTenant_IdOrderByCreatedAtDesc(Long tenantId);

    Optional<Webhook> findByIdAndTenant_Id(Long id, Long tenantId);
}
