package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.*;
import com.tunegocio.turnosapi.service.BlogService;
import com.tunegocio.turnosapi.service.SitioBuilderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Sitio Público", description = "Endpoints públicos para renderizar el sitio del tenant")
@RestController
@RequestMapping("/public/{slug}/site")
@RequiredArgsConstructor
public class PublicSiteController {

    private final SitioBuilderService sitioBuilderService;
    private final BlogService         blogService;

    // ─── Configuración del sitio ─────────────────────────────────────────────

    @Operation(summary = "Configuración pública del sitio (plantilla, paleta, SEO, módulos)")
    @GetMapping("/config")
    public ResponseEntity<SitioConfigDTO> config(@PathVariable String slug) {
        return ResponseEntity.ok(sitioBuilderService.obtenerConfig());
    }

    // ─── Páginas ─────────────────────────────────────────────────────────────

    @Operation(summary = "Menú de navegación del sitio")
    @GetMapping("/nav")
    public ResponseEntity<List<PaginaDTO>> nav(@PathVariable String slug) {
        return ResponseEntity.ok(sitioBuilderService.listarPaginas());
    }

    @Operation(summary = "Contenido de una página (secciones incluidas)")
    @GetMapping("/paginas/{pageSlug}")
    public ResponseEntity<PaginaDTO> pagina(
            @PathVariable String slug, @PathVariable String pageSlug) {
        return ResponseEntity.ok(sitioBuilderService.obtenerPaginaPorSlug(pageSlug));
    }

    // ─── Blog público ────────────────────────────────────────────────────────

    @Operation(summary = "Listado de posts publicados del blog")
    @GetMapping("/blog")
    public ResponseEntity<Page<BlogPostDTO>> blog(
            @PathVariable String slug,
            @RequestParam(required = false) Long categoriaId,
            @PageableDefault(size = 10, sort = "publicadoAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (categoriaId != null) {
            return ResponseEntity.ok(blogService.listarPorCategoria(categoriaId, pageable));
        }
        return ResponseEntity.ok(blogService.listarPublicados(pageable));
    }

    @Operation(summary = "Categorías del blog")
    @GetMapping("/blog/categorias")
    public ResponseEntity<List<BlogCategoriaDTO>> categoriasBlog(@PathVariable String slug) {
        return ResponseEntity.ok(blogService.listarCategorias());
    }

    @Operation(summary = "Post individual del blog (incrementa vistas)")
    @GetMapping("/blog/{postSlug}")
    public ResponseEntity<BlogPostDTO> post(
            @PathVariable String slug, @PathVariable String postSlug) {
        BlogPostDTO dto = blogService.obtenerPorSlug(postSlug);
        try { blogService.registrarVista(dto.id()); } catch (Exception ignored) {}
        return ResponseEntity.ok(dto);
    }
}
