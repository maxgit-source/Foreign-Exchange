package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.AuthResponseDTO;
import com.tunegocio.turnosapi.dto.LoginRequest;
import com.tunegocio.turnosapi.dto.RefreshTokenRequest;
import com.tunegocio.turnosapi.dto.RegisterRequest;
import com.tunegocio.turnosapi.entity.Plan;
import com.tunegocio.turnosapi.entity.Role;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.exception.ConflictException;
import com.tunegocio.turnosapi.exception.UnauthorizedException;
import com.tunegocio.turnosapi.repository.TenantRepository;
import com.tunegocio.turnosapi.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final SchemaService schemaService;
    private final SuscripcionService suscripcionService;
    private final AuditService auditService;

    @Transactional
    public AuthResponseDTO register(RegisterRequest request) {
        String ownerEmail = normalizeEmail(request.getEmail());
        String tenantEmail = normalizeEmail(request.getTenantEmail());

        if (usuarioRepository.existsByEmail(ownerEmail)) {
            throw new ConflictException("El email '" + ownerEmail + "' ya está registrado");
        }
        if (tenantRepository.existsByEmail(tenantEmail)) {
            throw new ConflictException("El email del negocio '" + tenantEmail + "' ya está registrado");
        }

        Tenant tenant = new Tenant();
        tenant.setNombre(request.getTenantNombre().trim());
        tenant.setEmail(tenantEmail);
        tenant.setSlug(generateUniqueSlug(request.getTenantNombre()));
        tenant.setPlan(Plan.FREE);
        tenant.setActivo(true);
        Usuario usuario = new Usuario();
        try {
            tenantRepository.save(tenant);

            usuario.setNombre(request.getNombre().trim());
            usuario.setEmail(ownerEmail);
            usuario.setPassword(passwordEncoder.encode(request.getPassword()));
            usuario.setRole(Role.OWNER);
            usuario.setTenant(tenant);
            usuario.setEnabled(true);
            usuarioRepository.save(usuario);

            schemaService.ensureSchemaForTenant(tenant.getSlug());
            suscripcionService.asegurarSuscripcionInicial(tenant);

            auditService.log(
                    "REGISTER",
                    "Tenant",
                    tenant.getId(),
                    tenant,
                    usuario,
                    Map.of(
                            "tenantId", tenant.getId(),
                            "tenantSlug", tenant.getSlug(),
                            "ownerId", usuario.getId()
                    )
            );

            log.info("Nuevo tenant registrado: slug='{}', owner='{}'", tenant.getSlug(), usuario.getEmail());
            return buildAuthResponse(usuario);
        } catch (RuntimeException ex) {
            if (tenant.getSlug() != null) {
                schemaService.dropSchemaQuietly(tenant.getSlug());
            }
            throw ex;
        }
    }

    public AuthResponseDTO authenticate(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado post-autenticación"));

        auditService.log("LOGIN", "Usuario", usuario.getId(), usuario, Map.of("email", usuario.getEmail()));
        log.debug("Login exitoso para usuario id={}", usuario.getId());
        return buildAuthResponse(usuario);
    }

    @Transactional
    public AuthResponseDTO refresh(RefreshTokenRequest request) {
        Usuario usuario = refreshTokenService.validarYRotar(request.getRefreshToken());
        return buildAuthResponse(usuario);
    }

    @Transactional
    public void logout(Usuario usuario) {
        if (usuario == null) {
            throw new UnauthorizedException("Debe autenticarse para cerrar sesión");
        }
        refreshTokenService.revocarTodos(usuario.getId());
        auditService.log("LOGOUT", "Usuario", usuario.getId(), usuario, null);
        log.debug("Logout para usuario id={}", usuario.getId());
    }

    private AuthResponseDTO buildAuthResponse(Usuario usuario) {
        String accessToken = jwtService.generateToken(usuario);
        String refreshToken = refreshTokenService.crear(usuario);

        return AuthResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMillis() / 1000)
                .usuarioId(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .role(usuario.getRole().name())
                .tenantId(usuario.getTenant().getId())
                .tenantNombre(usuario.getTenant().getNombre())
                .tenantSlug(usuario.getTenant().getSlug())
                .build();
    }

    private String generateUniqueSlug(String nombre) {
        String base = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-");

        String slug = base;
        int intentos = 0;

        while (tenantRepository.existsBySlug(slug)) {
            intentos++;
            slug = base + "-" + String.format("%04x", SECURE_RANDOM.nextInt(0x10000));
            if (intentos > 10) {
                throw new IllegalStateException("No se pudo generar un slug único para: " + nombre);
            }
        }

        return slug;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
