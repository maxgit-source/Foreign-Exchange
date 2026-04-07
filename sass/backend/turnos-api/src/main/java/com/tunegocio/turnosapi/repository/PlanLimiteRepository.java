package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.Plan;
import com.tunegocio.turnosapi.entity.PlanLimite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanLimiteRepository extends JpaRepository<PlanLimite, Plan> {
}
