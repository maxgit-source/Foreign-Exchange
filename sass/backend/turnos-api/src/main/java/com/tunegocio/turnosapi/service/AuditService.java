package com.tunegocio.turnosapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunegocio.turnosapi.entity.AuditLog;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void log(String accion, String entidad, Long entidadId, Usuario actor, Object detalle) {
        log(accion, entidad, entidadId,
                actor != null && actor.getTenant() != null ? actor.getTenant().getId() : null,
                actor != null ? actor.getId() : null,
                detalle);
    }

    public void log(String accion, String entidad, Long entidadId, Tenant tenant, Usuario actor, Object detalle) {
        log(accion, entidad, entidadId,
                tenant != null ? tenant.getId() : null,
                actor != null ? actor.getId() : null,
                detalle);
    }

    public void log(String accion, String entidad, Long entidadId, Long tenantId, Long usuarioId, Object detalle) {
        AuditLog entry = new AuditLog();
        entry.setAccion(accion);
        entry.setEntidad(entidad);
        entry.setEntidadId(entidadId);
        entry.setTenantId(tenantId);
        entry.setUsuarioId(usuarioId);
        entry.setDetalle(serialize(detalle));
        entry.setIpAddress(resolveClientIp());
        auditLogRepository.save(entry);
    }

    private String serialize(Object detalle) {
        if (detalle == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detalle);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"No se pudo serializar el detalle de auditoría\"}";
        }
    }

    private String resolveClientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }

        HttpServletRequest request = attributes.getRequest();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
