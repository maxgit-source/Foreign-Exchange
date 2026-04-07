package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.Role;
import com.tunegocio.turnosapi.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    Optional<Usuario> findByIdAndTenant_Id(Long id, Long tenantId);

    Optional<Usuario> findByIdAndTenant_IdAndRoleIn(Long id, Long tenantId, Collection<Role> roles);

    List<Usuario> findByTenant_IdAndRole(Long tenantId, Role role);

    List<Usuario> findByTenant_IdAndRoleInAndEnabledTrueOrderByNombreAsc(Long tenantId, List<Role> roles);

    List<Usuario> findByTenant_IdAndRoleInOrderByNombreAsc(Long tenantId, List<Role> roles);

    long countByTenant_IdAndRoleInAndEnabledTrue(Long tenantId, Collection<Role> roles);
}
