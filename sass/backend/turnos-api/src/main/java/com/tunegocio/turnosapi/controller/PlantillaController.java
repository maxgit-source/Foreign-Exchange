package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.PlantillaDTO;
import com.tunegocio.turnosapi.service.PlantillaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Plantillas", description = "Catálogo de plantillas disponibles para el sitio")
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class PlantillaController {

    private final PlantillaService plantillaService;

    @Operation(summary = "Listar todas las plantillas activas")
    @GetMapping
    public ResponseEntity<List<PlantillaDTO>> listar(
            @RequestParam(required = false) String categoria) {
        if (categoria != null && !categoria.isBlank()) {
            return ResponseEntity.ok(plantillaService.listarPorCategoria(categoria));
        }
        return ResponseEntity.ok(plantillaService.listarActivas());
    }

    @Operation(summary = "Obtener plantilla por código")
    @GetMapping("/{codigo}")
    public ResponseEntity<PlantillaDTO> obtener(@PathVariable String codigo) {
        return ResponseEntity.ok(plantillaService.obtenerPorCodigo(codigo));
    }
}
