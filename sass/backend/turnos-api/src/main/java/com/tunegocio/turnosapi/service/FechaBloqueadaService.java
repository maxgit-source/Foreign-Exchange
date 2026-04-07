package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.FechaBloqueadaRequestDTO;
import com.tunegocio.turnosapi.dto.FechaBloqueadaResponseDTO;
import com.tunegocio.turnosapi.entity.FechaBloqueada;
import com.tunegocio.turnosapi.entity.Role;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.exception.BusinessException;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.FechaBloqueadaRepository;
import com.tunegocio.turnosapi.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FechaBloqueadaService {

    private final FechaBloqueadaRepository fechaBloqueadaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<FechaBloqueadaResponseDTO> listar(Long profesionalId, Usuario actor) {
        validarProfesionalEnTenant(profesionalId, actor.getTenant().getId());
        return fechaBloqueadaRepository.findVisiblesByProfesionalId(profesionalId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public FechaBloqueadaResponseDTO bloquear(Long profesionalId, FechaBloqueadaRequestDTO dto, Usuario actor) {
        Usuario profesional = validarProfesionalEnTenant(profesionalId, actor.getTenant().getId());
        if (dto.getFechaFin().isBefore(dto.getFechaInicio())) {
            throw new BusinessException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }

        FechaBloqueada bloqueo = new FechaBloqueada();
        bloqueo.setProfesional(profesional);
        bloqueo.setFechaInicio(dto.getFechaInicio());
        bloqueo.setFechaFin(dto.getFechaFin());
        bloqueo.setMotivo(dto.getMotivo());

        FechaBloqueada guardado = fechaBloqueadaRepository.save(bloqueo);
        auditService.log("CREATE", "FechaBloqueada", guardado.getId(), actor,
                Map.of("profesionalId", profesionalId, "fechaInicio", dto.getFechaInicio().toString(), "fechaFin", dto.getFechaFin().toString()));
        return toDTO(guardado);
    }

    @Transactional
    public void eliminar(Long id, Usuario actor) {
        FechaBloqueada bloqueo = fechaBloqueadaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FechaBloqueada", id));

        if (bloqueo.getProfesional() != null
                && !bloqueo.getProfesional().getTenant().getId().equals(actor.getTenant().getId())) {
            throw new ResourceNotFoundException("FechaBloqueada", id);
        }

        fechaBloqueadaRepository.delete(bloqueo);
        auditService.log("DELETE", "FechaBloqueada", id, actor, null);
    }

    @Transactional(readOnly = true)
    public boolean existeBloqueo(Long profesionalId, java.time.LocalDate fecha) {
        return fechaBloqueadaRepository.existsBloqueoVisibleEnFecha(profesionalId, fecha);
    }

    private Usuario validarProfesionalEnTenant(Long profesionalId, Long tenantId) {
        Usuario profesional = usuarioRepository.findByIdAndTenant_IdAndRoleIn(
                        profesionalId,
                        tenantId,
                        List.of(Role.OWNER, Role.STAFF)
                )
                .orElseThrow(() -> new ResourceNotFoundException("Profesional", "id", profesionalId));

        if (!profesional.isEnabled()) {
            throw new BusinessException("El profesional seleccionado no está activo");
        }
        return profesional;
    }

    private FechaBloqueadaResponseDTO toDTO(FechaBloqueada bloqueo) {
        return FechaBloqueadaResponseDTO.builder()
                .id(bloqueo.getId())
                .profesionalId(bloqueo.getProfesional() != null ? bloqueo.getProfesional().getId() : null)
                .nombreProfesional(bloqueo.getProfesional() != null ? bloqueo.getProfesional().getNombre() : null)
                .fechaInicio(bloqueo.getFechaInicio())
                .fechaFin(bloqueo.getFechaFin())
                .motivo(bloqueo.getMotivo())
                .aplicaATodoElTenant(bloqueo.getProfesional() == null)
                .createdAt(bloqueo.getCreatedAt())
                .build();
    }
}
