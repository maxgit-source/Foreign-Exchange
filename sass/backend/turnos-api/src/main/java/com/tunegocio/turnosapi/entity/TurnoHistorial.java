package com.tunegocio.turnosapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "turno_historial",
        indexes = {
                @Index(name = "idx_turno_historial_turno_created", columnList = "turno_id, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TurnoHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20)
    private TurnoStatus estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private TurnoStatus estadoNuevo;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "fecha_anterior")
    private Instant fechaAnterior;

    @Column(name = "fecha_nueva")
    private Instant fechaNueva;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
