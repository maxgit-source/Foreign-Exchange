package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.PedidoEstadoUpdateDTO;
import com.tunegocio.turnosapi.dto.PedidoRequestDTO;
import com.tunegocio.turnosapi.dto.PedidoResponseDTO;
import com.tunegocio.turnosapi.entity.PedidoEstado;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.service.PedidoService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pedidos", description = "Gestion de pedidos y cobros")
@RestController
@RequestMapping("/api/pedidos")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    @Operation(summary = "Listar pedidos del tenant")
    @GetMapping
    public ResponseEntity<Page<PedidoResponseDTO>> listar(
            @RequestParam(required = false) PedidoEstado estado,
            @RequestParam(required = false) String busqueda,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(pedidoService.listar(estado, busqueda, actor, pageable));
    }

    @Operation(summary = "Obtener pedido por ID")
    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponseDTO> obtener(@PathVariable Long id, @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(pedidoService.obtener(id, actor));
    }

    @Operation(summary = "Crear pedido interno")
    @PostMapping
    public ResponseEntity<PedidoResponseDTO> crear(
            @Valid @RequestBody PedidoRequestDTO dto,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.crear(dto, actor));
    }

    @Operation(summary = "Cambiar estado del pedido")
    @PatchMapping("/{id}/estado")
    public ResponseEntity<PedidoResponseDTO> actualizarEstado(
            @PathVariable Long id,
            @Valid @RequestBody PedidoEstadoUpdateDTO dto,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(pedidoService.actualizarEstado(id, dto, actor));
    }
}
