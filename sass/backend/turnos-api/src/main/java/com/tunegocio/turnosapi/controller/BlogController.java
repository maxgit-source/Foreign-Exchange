package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.*;
import com.tunegocio.turnosapi.service.BlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Blog", description = "Gestión del blog del tenant")
@RestController
@RequestMapping("/api/blog")
@RequiredArgsConstructor
public class BlogController {

    private final BlogService blogService;

    // ─── Categorías ──────────────────────────────────────────────────────────

    @Operation(summary = "Listar categorías del blog")
    @GetMapping("/categorias")
    public ResponseEntity<List<BlogCategoriaDTO>> listarCategorias() {
        return ResponseEntity.ok(blogService.listarCategorias());
    }

    @Operation(summary = "Crear categoría")
    @PostMapping("/categorias")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<BlogCategoriaDTO> crearCategoria(
            @Valid @RequestBody BlogCategoriaCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(blogService.crearCategoria(dto));
    }

    @Operation(summary = "Eliminar categoría")
    @DeleteMapping("/categorias/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> eliminarCategoria(@PathVariable Long id) {
        blogService.eliminarCategoria(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Posts (admin) ───────────────────────────────────────────────────────

    @Operation(summary = "Listar todos los posts (admin)")
    @GetMapping("/admin/posts")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Page<BlogPostDTO>> listarTodos(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(blogService.listarTodos(pageable));
    }

    @Operation(summary = "Obtener post por ID (admin)")
    @GetMapping("/admin/posts/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<BlogPostDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(blogService.obtenerPorId(id));
    }

    @Operation(summary = "Crear post")
    @PostMapping("/posts")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<BlogPostDTO> crear(@Valid @RequestBody BlogPostCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(blogService.crear(dto));
    }

    @Operation(summary = "Actualizar post")
    @PutMapping("/posts/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<BlogPostDTO> actualizar(
            @PathVariable Long id, @Valid @RequestBody BlogPostCreateDTO dto) {
        return ResponseEntity.ok(blogService.actualizar(id, dto));
    }

    @Operation(summary = "Publicar post")
    @PostMapping("/posts/{id}/publicar")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<BlogPostDTO> publicar(@PathVariable Long id) {
        return ResponseEntity.ok(blogService.publicar(id));
    }

    @Operation(summary = "Archivar post")
    @PostMapping("/posts/{id}/archivar")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<BlogPostDTO> archivar(@PathVariable Long id) {
        return ResponseEntity.ok(blogService.archivar(id));
    }

    @Operation(summary = "Eliminar post")
    @DeleteMapping("/posts/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        blogService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
