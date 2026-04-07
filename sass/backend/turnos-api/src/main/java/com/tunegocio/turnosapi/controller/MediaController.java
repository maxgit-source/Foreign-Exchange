package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.MediaFileDTO;
import com.tunegocio.turnosapi.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Media Library", description = "Biblioteca de archivos multimedia del tenant")
@RestController
@RequestMapping("/api/media")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @Operation(summary = "Listar archivos de la biblioteca")
    @GetMapping
    public ResponseEntity<Page<MediaFileDTO>> listar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String carpeta,
            @PageableDefault(size = 24, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(mediaService.listar(tipo, carpeta, pageable));
    }

    @Operation(summary = "Listar carpetas disponibles")
    @GetMapping("/carpetas")
    public ResponseEntity<List<String>> listarCarpetas() {
        return ResponseEntity.ok(mediaService.listarCarpetas());
    }

    @Operation(summary = "Subir archivo a la biblioteca")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaFileDTO> subir(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String carpeta,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String tags) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaService.subir(file, carpeta, nombre, tags));
    }

    @Operation(summary = "Eliminar archivo de la biblioteca")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        mediaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
