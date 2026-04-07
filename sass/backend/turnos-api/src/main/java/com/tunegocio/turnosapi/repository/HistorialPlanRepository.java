package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.HistorialPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistorialPlanRepository extends JpaRepository<HistorialPlan, Long> {

    List<HistorialPlan> findByTenant_IdOrderByCreatedAtDesc(Long tenantId);
}
