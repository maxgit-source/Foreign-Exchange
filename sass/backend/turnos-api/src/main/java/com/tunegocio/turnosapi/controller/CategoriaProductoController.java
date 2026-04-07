package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.CategoriaProductoRequestDTO;
import com.tunegocio.turnosapi.dto.CategoriaProductoResponseDTO;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.service.CategoriaProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Categorias de Producto", description = "Gestion de categorias del catalogo")
@RestController
@RequestMapping("/api/categorias-producto")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
@RequiredArgsConstructor
public class CategoriaProductoController {

    private final CategoriaProductoService categoriaProductoService;

    @Operation(summary = "Listar categorias del catalogo")
    @GetMapping
    public ResponseEntity<List<CategoriaProductoResponseDTO>> listar(
            @RequestParam(defaultValue = "false") boolean incluirInactivas,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(categoriaProductoService.listar(incluirInactivas, actor));
    }

    @Operation(summary = "Crear categoria de producto")
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<CategoriaProductoResponseDTO> crear(
            @Valid @RequestBody CategoriaProductoRequestDTO dto,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoriaProductoService.crear(dto, actor));
    }

    @Operation(summary = "Actualizar categoria de producto")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<CategoriaProductoResponseDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody CategoriaProductoRequestDTO dto,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(categoriaProductoService.actualizar(id, dto, actor));
    }

    @Operation(summary = "Desactivar categoria de producto")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal Usuario actor) {
        categoriaProductoService.eliminar(id, actor);
        return ResponseEntity.noContent().build();
    }
}
