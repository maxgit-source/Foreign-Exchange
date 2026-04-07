package com.tunegocio.turnosapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro de auditoría de notificaciones enviadas (tenant-scoped).
 */
@Entity
@Table(name = "notificaciones_log")
@Getter
@Setter
@NoArgsConstructor
public class NotificacionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(name = "destinatario_email", nullable = false, length = 200)
    private String destinatarioEmail;

    @Column(length = 500)
    private String asunto;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "error_detalle", columnDefinition = "TEXT")
    private String errorDetalle;

    @Column(name = "turno_id")
    private Long turnoId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── Factory methods ─────────────────────────────────────────────────────

    public static NotificacionLog enviado(String tipo, String email, String asunto, Long turnoId) {
        NotificacionLog log = new NotificacionLog();
        log.tipo            = tipo;
        log.destinatarioEmail = email;
        log.asunto          = asunto;
        log.estado          = "ENVIADO";
        log.turnoId         = turnoId;
        return log;
    }

    public static NotificacionLog error(String tipo, String email, String asunto, Long turnoId, String detalle) {
        NotificacionLog log = new NotificacionLog();
        log.tipo            = tipo;
        log.destinatarioEmail = email;
        log.asunto          = asunto;
        log.estado          = "ERROR";
        log.turnoId         = turnoId;
        log.errorDetalle    = detalle;
        return log;
    }
}
