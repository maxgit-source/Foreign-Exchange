package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.entity.RefreshToken;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.exception.UnauthorizedException;
import com.tunegocio.turnosapi.repository.RefreshTokenRepository;
import com.tunegocio.turnosapi.util.Sha256Hasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-days:7}")
    private int refreshExpirationDays;

    @Transactional
    public String crear(Usuario usuario) {
        refreshTokenRepository.revokeAllByUsuarioId(usuario.getId());

        String rawToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUsuario(usuario);
        refreshToken.setTokenHash(Sha256Hasher.hash(rawToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(refreshExpirationDays));
        refreshToken.setRevoked(false);

        refreshTokenRepository.save(refreshToken);
        log.debug("Refresh token creado para usuario id={}", usuario.getId());
        return rawToken;
    }

    @Transactional
    public Usuario validarYRotar(String rawToken) {
        RefreshToken rt = refreshTokenRepository.findByTokenHashAndRevokedFalse(Sha256Hasher.hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Refresh token inválido o ya utilizado"));

        if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            throw new UnauthorizedException("Refresh token expirado. Por favor vuelva a iniciar sesión");
        }

        rt.setRevoked(true);
        refreshTokenRepository.save(rt);
        return rt.getUsuario();
    }

    @Transactional
    public void revocarTodos(Long usuarioId) {
        refreshTokenRepository.revokeAllByUsuarioId(usuarioId);
        log.debug("Todos los refresh tokens revocados para usuario id={}", usuarioId);
    }
}
