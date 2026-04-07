package com.tunegocio.turnosapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "paginas")
@Getter
@Setter
@NoArgsConstructor
public class Pagina extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String slug;

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "es_home", nullable = false)
    private boolean esHome = false;

    @Column(name = "en_nav", nullable = false)
    private boolean enNav = true;

    @Column(name = "orden_nav", nullable = false)
    private int ordenNav = 0;

    @Column(name = "seo_titulo", length = 255)
    private String seoTitulo;

    @Column(name = "seo_descripcion", columnDefinition = "TEXT")
    private String seoDescripcion;

    @Column(name = "og_imagen_url", columnDefinition = "TEXT")
    private String ogImagenUrl;

    @OneToMany(mappedBy = "pagina", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orden ASC")
    private List<Seccion> secciones = new ArrayList<>();
}
