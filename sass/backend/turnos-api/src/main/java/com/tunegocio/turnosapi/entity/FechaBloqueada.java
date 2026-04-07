package com.tunegocio.turnosapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
        name = "fechas_bloqueadas",
        indexes = {
                @Index(name = "idx_fecha_bloqueada_profesional", columnList = "profesional_id, fecha_inicio, fecha_fin"),
                @Index(name = "idx_fecha_bloqueada_rango", columnList = "fecha_inicio, fecha_fin")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class FechaBloqueada extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profesional_id")
    private Usuario profesional;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(length = 200)
    private String motivo;
}
