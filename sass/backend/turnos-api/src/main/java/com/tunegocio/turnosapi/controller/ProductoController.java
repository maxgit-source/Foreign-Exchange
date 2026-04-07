package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.ProductoRequestDTO;
import com.tunegocio.turnosapi.dto.ProductoResponseDTO;
import com.tunegocio.turnosapi.dto.ProductoStockUpdateDTO;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "Productos", description = "Catalogo de productos del negocio")
@RestController
@RequestMapping("/api/productos")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @Operation(summary = "Listar productos con filtros y paginacion")
    @GetMapping
    public ResponseEntity<Page<ProductoResponseDTO>> listar(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) BigDecimal precioMin,
            @RequestParam(required = false) BigDecimal precioMax,
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(
                productoService.listar(nombre, categoriaId, precioMin, precioMax, incluirInactivos, actor, pageable)
        );
    }

    @Operation(summary = "Obtener producto por ID")
    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> obtener(@PathVariable Long id, @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(productoService.obtener(id, actor));
    }

    @Operation(summary = "Crear producto")
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ProductoResponseDTO> crear(
            @Valid @RequestBody ProductoRequestDTO dto,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productoService.crear(dto, actor));
    }

    @Operation(summary = "Actualizar producto")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ProductoResponseDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProductoRequestDTO dto,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(productoService.actualizar(id, dto, actor));
    }

    @Operation(summary = "Actualizar stock")
    @PatchMapping("/{id}/stock")
    public ResponseEntity<ProductoResponseDTO> actualizarStock(
            @PathVariable Long id,
            @Valid @RequestBody ProductoStockUpdateDTO dto,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(productoService.actualizarStock(id, dto, actor));
    }

    @Operation(summary = "Subir imagenes del producto")
    @PostMapping(value = "/{id}/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ProductoResponseDTO> agregarImagenes(
            @PathVariable Long id,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal Usuario actor) {
        List<MultipartFile> archivos = new ArrayList<>();
        if (files != null) {
            archivos.addAll(files);
        }
        if (file != null) {
            archivos.add(file);
        }
        return ResponseEntity.ok(productoService.agregarImagenes(id, archivos, actor));
    }

    @Operation(summary = "Desactivar producto")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal Usuario actor) {
        productoService.eliminar(id, actor);
        return ResponseEntity.noContent().build();
    }
}
