package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHashAndActivoTrue(String keyHash);

    List<ApiKey> findByTenant_IdOrderByCreatedAtDesc(Long tenantId);

    Optional<ApiKey> findByIdAndTenant_Id(Long id, Long tenantId);
}
