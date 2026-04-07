package com.tunegocio.turnosapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sitio_config")
@Getter
@Setter
@NoArgsConstructor
public class SitioConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plantilla_codigo", nullable = false, length = 100)
    private String plantillaCodigo;

    @Column(name = "dominio_custom", length = 255)
    private String dominioCustom;

    @Column(name = "nombre_sitio", length = 255)
    private String nombreSitio;

    @Column(name = "descripcion_sitio", columnDefinition = "TEXT")
    private String descripcionSitio;

    @Column(name = "favicon_url", columnDefinition = "TEXT")
    private String faviconUrl;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    /** JSON: {"primario":"#FF5722","secundario":"#FFC107","fondo":"#fff","texto":"#111","acento":"#E91E63"} */
    @Column(columnDefinition = "TEXT")
    private String paleta;

    /** JSON: {"instagram":"url","facebook":"url","twitter":"url","whatsapp":"+54..."} */
    @Column(name = "redes_sociales", columnDefinition = "TEXT")
    private String redesSociales;

    @Column(name = "seo_titulo", length = 255)
    private String seoTitulo;

    @Column(name = "seo_descripcion", columnDefinition = "TEXT")
    private String seoDescripcion;

    @Column(name = "seo_keywords", columnDefinition = "TEXT")
    private String seoKeywords;

    @Column(name = "og_imagen_url", columnDefinition = "TEXT")
    private String ogImagenUrl;

    @Column(name = "fuente_principal", length = 100)
    private String fuentePrincipal = "Inter";

    @Column(name = "fuente_titulos", length = 100)
    private String fuenteTitulos = "Inter";

    @Column(name = "animaciones_activas", nullable = false)
    private boolean animacionesActivas = true;

    @Column(name = "modo_oscuro_default", nullable = false)
    private boolean modoOscuroDefault = false;

    @Column(name = "modulo_tienda", nullable = false)
    private boolean moduloTienda = true;

    @Column(name = "modulo_blog", nullable = false)
    private boolean moduloBlog = false;

    @Column(name = "modulo_turnos", nullable = false)
    private boolean moduloTurnos = true;

    @Column(name = "sitio_activo", nullable = false)
    private boolean sitioActivo = true;

    @Column(name = "mensaje_mantenimiento", columnDefinition = "TEXT")
    private String mensajeMantenimiento;

    @Column(name = "config_extra", columnDefinition = "TEXT")
    private String configExtra;
}
