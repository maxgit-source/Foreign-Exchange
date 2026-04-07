package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.*;
import com.tunegocio.turnosapi.entity.Role;
import com.tunegocio.turnosapi.entity.StaffInvitation;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.exception.ConflictException;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.exception.UnauthorizedException;
import com.tunegocio.turnosapi.repository.StaffInvitationRepository;
import com.tunegocio.turnosapi.repository.UsuarioRepository;
import com.tunegocio.turnosapi.util.Sha256Hasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final UsuarioRepository usuarioRepository;
    private final StaffInvitationRepository staffInvitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificacionService notificacionService;
    private final RefreshTokenService refreshTokenService;
    private final UploadService uploadService;
    private final PlanValidator planValidator;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<StaffResponseDTO> listar(Usuario actor) {
        return usuarioRepository.findByTenant_IdAndRoleInOrderByNombreAsc(
                        actor.getTenant().getId(),
                        List.of(Role.OWNER, Role.STAFF)
                ).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public StaffResponseDTO crear(StaffCreateDTO dto, Usuario actor) {
        planValidator.validarPuedeCrearProfesional(actor.getTenant());
        String email = normalizeEmail(dto.getEmail());
        validarEmailDisponible(email, null);

        Usuario staff = new Usuario();
        staff.setNombre(dto.getNombre().trim());
        staff.setEmail(email);
        staff.setPassword(passwordEncoder.encode(dto.getPassword()));
        staff.setRole(Role.STAFF);
        staff.setTenant(actor.getTenant());
        staff.setEnabled(true);

        Usuario guardado = usuarioRepository.save(staff);
        auditService.log("CREATE", "Staff", guardado.getId(), actor, Map.of("email", guardado.getEmail()));
        notificacionService.enviarBienvenidaStaff(guardado);
        return toDTO(guardado);
    }

    @Transactional
    public StaffResponseDTO actualizar(Long id, StaffUpdateDTO dto, Usuario actor) {
        Usuario staff = buscarStaffEditable(id, actor.getTenant().getId());
        String email = normalizeEmail(dto.getEmail());
        validarEmailDisponible(email, staff.getId());

        staff.setNombre(dto.getNombre().trim());
        staff.setEmail(email);
        staff.setEnabled(dto.getEnabled());

        Usuario guardado = usuarioRepository.save(staff);
        if (!guardado.isEnabled()) {
            refreshTokenService.revocarTodos(guardado.getId());
        }

        auditService.log("UPDATE", "Staff", guardado.getId(), actor, Map.of("enabled", guardado.isEnabled()));
        return toDTO(guardado);
    }

    @Transactional
    public StaffResponseDTO actualizarFoto(Long id, MultipartFile file, Usuario actor) {
        Usuario staff = buscarMiembroDeEquipo(id, actor.getTenant().getId());
        String fotoUrl = uploadService.uploadImage(file, "staff");
        staff.setFotoUrl(fotoUrl);
        Usuario guardado = usuarioRepository.save(staff);
        auditService.log("UPDATE", "Staff", guardado.getId(), actor, Map.of("fotoUrl", fotoUrl));
        return toDTO(guardado);
    }

    @Transactional
    public void deshabilitar(Long id, Usuario actor) {
        Usuario staff = buscarStaffEditable(id, actor.getTenant().getId());
        staff.setEnabled(false);
        usuarioRepository.save(staff);
        refreshTokenService.revocarTodos(staff.getId());
        auditService.log("DELETE", "Staff", staff.getId(), actor, Map.of("enabled", false));
    }

    @Transactional
    public void invitar(StaffInvitacionDTO dto, Usuario actor) {
        String email = normalizeEmail(dto.getEmail());
        validarEmailDisponible(email, null);

        StaffInvitation invitation = staffInvitationRepository
                .findByEmailIgnoreCaseAndTenant_Id(email, actor.getTenant().getId())
                .orElseGet(StaffInvitation::new);

        String rawToken = UUID.randomUUID().toString();
        invitation.setTenant(actor.getTenant());
        invitation.setEmail(email);
        invitation.setTokenHash(Sha256Hasher.hash(rawToken));
        invitation.setExpiresAt(LocalDateTime.now().plusDays(7));
        invitation.setUsado(false);
        staffInvitationRepository.save(invitation);

        auditService.log("INVITE", "StaffInvitation", invitation.getId(), actor, Map.of("email", email));
        notificacionService.enviarInvitacionStaff(email, actor.getTenant(), rawToken, invitation.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public StaffInvitacionPreviewDTO obtenerInvitacion(String rawToken) {
        StaffInvitation invitation = validarInvitacion(rawToken);
        return StaffInvitacionPreviewDTO.builder()
                .email(invitation.getEmail())
                .tenantNombre(invitation.getTenant().getNombre())
                .expiresAt(invitation.getExpiresAt())
                .build();
    }

    @Transactional
    public StaffResponseDTO aceptarInvitacion(StaffInvitacionAceptacionRequestDTO dto) {
        StaffInvitation invitation = validarInvitacion(dto.getToken());
        planValidator.validarPuedeCrearProfesional(invitation.getTenant());
        String email = normalizeEmail(invitation.getEmail());
        validarEmailDisponible(email, null);

        Usuario staff = new Usuario();
        staff.setNombre(dto.getNombre().trim());
        staff.setEmail(email);
        staff.setPassword(passwordEncoder.encode(dto.getPassword()));
        staff.setRole(Role.STAFF);
        staff.setTenant(invitation.getTenant());
        staff.setEnabled(true);

        Usuario guardado = usuarioRepository.save(staff);
        invitation.setUsado(true);
        staffInvitationRepository.save(invitation);

        auditService.log(
                "ACCEPT_INVITATION",
                "Staff",
                guardado.getId(),
                invitation.getTenant(),
                guardado,
                Map.of("email", guardado.getEmail())
        );
        notificacionService.enviarBienvenidaStaff(guardado);
        return toDTO(guardado);
    }

    private Usuario buscarStaffEditable(Long id, Long tenantId) {
        return usuarioRepository.findByIdAndTenant_IdAndRoleIn(id, tenantId, List.of(Role.STAFF))
                .orElseThrow(() -> new ResourceNotFoundException("Staff", id));
    }

    private Usuario buscarMiembroDeEquipo(Long id, Long tenantId) {
        return usuarioRepository.findByIdAndTenant_IdAndRoleIn(id, tenantId, List.of(Role.OWNER, Role.STAFF))
                .orElseThrow(() -> new ResourceNotFoundException("Staff", id));
    }

    private StaffInvitation validarInvitacion(String rawToken) {
        StaffInvitation invitation = staffInvitationRepository
                .findByTokenHashAndUsadoFalse(Sha256Hasher.hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("La invitación es inválida o ya fue utilizada"));

        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("La invitación expiró. Solicite una nueva.");
        }

        return invitation;
    }

    private void validarEmailDisponible(String email, Long excludingUserId) {
        boolean occupied = excludingUserId == null
                ? usuarioRepository.existsByEmail(email)
                : usuarioRepository.existsByEmailAndIdNot(email, excludingUserId);

        if (occupied) {
            throw new ConflictException("El email '" + email + "' ya está registrado en la plataforma");
        }
    }

    private StaffResponseDTO toDTO(Usuario usuario) {
        return StaffResponseDTO.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .role(usuario.getRole().name())
                .fotoUrl(usuario.getFotoUrl())
                .enabled(usuario.isEnabled())
                .createdAt(usuario.getCreatedAt())
                .build();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
