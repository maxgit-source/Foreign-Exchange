package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.*;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.service.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Staff", description = "Gestión del equipo de trabajo del negocio")
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @Operation(summary = "Listar staff del tenant")
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<List<StaffResponseDTO>> listar(@AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(staffService.listar(actor));
    }

    @Operation(summary = "Crear usuario staff")
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<StaffResponseDTO> crear(@Valid @RequestBody StaffCreateDTO dto,
                                                  @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.crear(dto, actor));
    }

    @Operation(summary = "Actualizar usuario staff")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<StaffResponseDTO> actualizar(@PathVariable Long id,
                                                       @Valid @RequestBody StaffUpdateDTO dto,
                                                       @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(staffService.actualizar(id, dto, actor));
    }

    @Operation(summary = "Subir foto de perfil de staff")
    @PostMapping(value = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER') or #id == authentication.principal.id")
    public ResponseEntity<StaffResponseDTO> actualizarFoto(@PathVariable Long id,
                                                           @RequestPart("file") MultipartFile file,
                                                           @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok(staffService.actualizarFoto(id, file, actor));
    }

    @Operation(summary = "Deshabilitar usuario staff")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> deshabilitar(@PathVariable Long id,
                                             @AuthenticationPrincipal Usuario actor) {
        staffService.deshabilitar(id, actor);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Invitar staff por email")
    @PostMapping("/invitar")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> invitar(@Valid @RequestBody StaffInvitacionDTO dto,
                                        @AuthenticationPrincipal Usuario actor) {
        staffService.invitar(dto, actor);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Validar invitación de staff")
    @GetMapping("/invitaciones/aceptar")
    public ResponseEntity<StaffInvitacionPreviewDTO> obtenerInvitacion(@RequestParam String token) {
        return ResponseEntity.ok(staffService.obtenerInvitacion(token));
    }

    @Operation(summary = "Aceptar invitación de staff")
    @PostMapping("/invitaciones/aceptar")
    public ResponseEntity<StaffResponseDTO> aceptarInvitacion(
            @Valid @RequestBody StaffInvitacionAceptacionRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.aceptarInvitacion(dto));
    }
}
