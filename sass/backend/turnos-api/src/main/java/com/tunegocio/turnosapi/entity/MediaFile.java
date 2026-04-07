package com.tunegocio.turnosapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "media_files")
@Getter
@Setter
@NoArgsConstructor
public class MediaFile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cloudinary_id", nullable = false, length = 500)
    private String cloudinaryId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(nullable = false, length = 500)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String tipo = "image";

    @Column(length = 50)
    private String formato;

    @Column(name = "tamanio_bytes")
    private Long tamanioBytes;

    @Column
    private Integer ancho;

    @Column
    private Integer alto;

    @Column(length = 255)
    private String carpeta = "general";

    @Column(columnDefinition = "TEXT")
    private String tags;
}
