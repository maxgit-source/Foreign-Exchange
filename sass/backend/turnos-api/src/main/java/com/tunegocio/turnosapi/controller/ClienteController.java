package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.ClienteRequestDTO;
import com.tunegocio.turnosapi.dto.ClienteResponseDTO;
import com.tunegocio.turnosapi.dto.TurnoResponseDTO;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.service.ClienteService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Clientes", description = "Gestión de clientes del negocio")
@RestController
@RequestMapping("/api/clientes")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @Operation(summary = "Listar clientes con búsqueda y paginación")
    @GetMapping
    public ResponseEntity<Page<ClienteResponseDTO>> listar(
            @RequestParam(required = false) String busqueda,
            @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(clienteService.listar(busqueda, pageable));
    }

    @Operation(summary = "Obtener cliente por ID")
    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponseDTO> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.obtenerPorId(id));
    }

    @Operation(summary = "Ver historial de turnos de un cliente")
    @GetMapping("/{id}/historial")
    public ResponseEntity<List<TurnoResponseDTO>> historial(@PathVariable Long id,
                                                            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(clienteService.historial(id, actor));
    }

    @Operation(summary = "Crear nuevo cliente")
    @PostMapping
    public ResponseEntity<ClienteResponseDTO> crear(@Valid @RequestBody ClienteRequestDTO dto,
                                                    @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.crear(dto, actor));
    }

    @Operation(summary = "Actualizar datos del cliente")
    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponseDTO> actualizar(@PathVariable Long id,
                                                         @Valid @RequestBody ClienteRequestDTO dto,
                                                         @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(clienteService.actualizar(id, dto, actor));
    }

    @Operation(summary = "Archivar cliente")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archivar(@PathVariable Long id,
                                         @AuthenticationPrincipal Usuario actor) {
        clienteService.archivar(id, actor);
        return ResponseEntity.noContent().build();
    }
}
