package com.tunegocio.turnosapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plantillas", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class Plantilla extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, length = 100)
    private String categoria;

    @Column(name = "preview_url", columnDefinition = "TEXT")
    private String previewUrl;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(nullable = false)
    private int orden = 0;

    @Column(name = "config_default", columnDefinition = "TEXT")
    private String configDefault;
}
