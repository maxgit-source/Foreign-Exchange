package com.tunegocio.turnosapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Bloque semanal de disponibilidad de un profesional del tenant actual.
 */
@Entity
@Table(name = "disponibilidad_profesional")
@Getter
@Setter
@NoArgsConstructor
public class DisponibilidadProfesional extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profesional_id", nullable = false)
    private Usuario profesional;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private DiaSemana dia;

    @Column(nullable = false)
    private LocalTime horaInicio;

    @Column(nullable = false)
    private LocalTime horaFin;

    @Column(nullable = false)
    private boolean activo = true;
}
