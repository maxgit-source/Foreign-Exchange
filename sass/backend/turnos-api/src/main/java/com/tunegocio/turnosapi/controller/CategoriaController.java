package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.CategoriaRequestDTO;
import com.tunegocio.turnosapi.dto.CategoriaResponseDTO;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Categorias", description = "Clasificación del catálogo de servicios")
@RestController
@RequestMapping("/api/categorias")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @Operation(summary = "Listar categorías")
    @GetMapping
    public ResponseEntity<List<CategoriaResponseDTO>> listar(
            @RequestParam(defaultValue = "false") boolean incluirInactivas) {
        return ResponseEntity.ok(categoriaService.listar(incluirInactivas));
    }

    @Operation(summary = "Crear categoría")
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<CategoriaResponseDTO> crear(@Valid @RequestBody CategoriaRequestDTO dto,
                                                      @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoriaService.crear(dto, actor));
    }

    @Operation(summary = "Actualizar categoría")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<CategoriaResponseDTO> actualizar(@PathVariable Long id,
                                                           @Valid @RequestBody CategoriaRequestDTO dto,
                                                           @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(categoriaService.actualizar(id, dto, actor));
    }

    @Operation(summary = "Desactivar categoría")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id,
                                         @AuthenticationPrincipal Usuario actor) {
        categoriaService.eliminar(id, actor);
        return ResponseEntity.noContent().build();
    }
}
