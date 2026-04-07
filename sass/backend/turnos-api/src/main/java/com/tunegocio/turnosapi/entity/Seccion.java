package com.tunegocio.turnosapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "secciones")
@Getter
@Setter
@NoArgsConstructor
public class Seccion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pagina_id", nullable = false)
    private Pagina pagina;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SeccionTipo tipo;

    @Column(nullable = false)
    private int orden = 0;

    @Column(nullable = false)
    private boolean visible = true;

    /** JSON config específica del tipo de sección */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String config = "{}";

    @Column(name = "animacion_entrada", length = 100)
    private String animacionEntrada;

    @Column(name = "animacion_duracion")
    private Integer animacionDuracion = 600;

    @Column(name = "animacion_delay")
    private Integer animacionDelay = 0;

    /** JSON estilos custom: bg_color, text_color, padding, etc. */
    @Column(columnDefinition = "TEXT")
    private String estilos = "{}";
}
