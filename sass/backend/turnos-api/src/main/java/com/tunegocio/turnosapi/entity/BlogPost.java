package com.tunegocio.turnosapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "blog_posts")
@Getter
@Setter
@NoArgsConstructor
public class BlogPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(nullable = false, unique = true, length = 500)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String extracto;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "imagen_portada", columnDefinition = "TEXT")
    private String imagenPortada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlogEstado estado = BlogEstado.BORRADOR;

    @Column(name = "publicado_at")
    private LocalDateTime publicadoAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private BlogCategoria categoria;

    @Column(name = "autor_nombre", length = 255)
    private String autorNombre;

    @Column(name = "seo_titulo", length = 255)
    private String seoTitulo;

    @Column(name = "seo_descripcion", columnDefinition = "TEXT")
    private String seoDescripcion;

    @Column(name = "og_imagen_url", columnDefinition = "TEXT")
    private String ogImagenUrl;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(nullable = false)
    private long vistas = 0;
}
