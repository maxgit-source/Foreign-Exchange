package com.tunegocio.turnosapi.repository;

import com.tunegocio.turnosapi.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    /** Revoca todos los tokens activos del usuario (usado en logout y rotación total). */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.usuario.id = :usuarioId AND rt.revoked = false")
    void revokeAllByUsuarioId(@Param("usuarioId") Long usuarioId);
}
