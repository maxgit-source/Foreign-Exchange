package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.StaffInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffInvitationRepository extends JpaRepository<StaffInvitation, Long> {

    Optional<StaffInvitation> findByTokenHashAndUsadoFalse(String tokenHash);

    Optional<StaffInvitation> findByEmailIgnoreCaseAndTenant_Id(String email, Long tenantId);
}
