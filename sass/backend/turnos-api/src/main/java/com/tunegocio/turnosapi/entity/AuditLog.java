package com.tunegocio.turnosapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        schema = "public",
        name = "audit_log",
        indexes = {
                @Index(name = "idx_audit_tenant_created", columnList = "tenant_id, created_at"),
                @Index(name = "idx_audit_usuario_created", columnList = "usuario_id, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 30)
    private String accion;

    @Column(nullable = false, length = 120)
    private String entidad;

    @Column(name = "entidad_id")
    private Long entidadId;

    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
