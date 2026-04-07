package com.tunegocio.turnosapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Turno/cita del tenant actual.
 */
@Entity
@Table(
        name = "turnos",
        indexes = {
                @Index(name = "idx_turno_profesional_fecha", columnList = "profesional_id, fecha_hora_inicio"),
                @Index(name = "idx_turno_cliente", columnList = "cliente_id"),
                @Index(name = "idx_turno_fecha", columnList = "fecha_hora_inicio")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Turno extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profesional_id", nullable = false)
    private Usuario profesional;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "turno_servicios",
            joinColumns = @JoinColumn(name = "turno_id"),
            inverseJoinColumns = @JoinColumn(name = "servicio_id")
    )
    @OrderBy("nombre ASC")
    private Set<Servicio> servicios = new LinkedHashSet<>();

    @Column(nullable = false)
    private Instant fechaHoraInicio;

    @Column(nullable = false)
    private Instant fechaHoraFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TurnoStatus estado = TurnoStatus.PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_padre_id")
    private Turno turnoPadre;

    @Column(name = "es_recurrente", nullable = false)
    private boolean recurrente = false;

    @Column(name = "recurrencia_semanas")
    private Integer recurrenciaSemanas;

    @Column(nullable = false)
    private boolean recordatorio24hEnviado = false;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Transient
    public int getDuracionTotalMinutos() {
        return servicios.stream()
                .mapToInt(Servicio::getDuracionMinutos)
                .sum();
    }

    @Transient
    public BigDecimal getPrecioTotal() {
        return servicios.stream()
                .map(Servicio::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
