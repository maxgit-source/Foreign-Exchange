package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.AuthResponseDTO;
import com.tunegocio.turnosapi.dto.LoginRequest;
import com.tunegocio.turnosapi.dto.RefreshTokenRequest;
import com.tunegocio.turnosapi.dto.RegisterRequest;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Autenticación", description = "Registro, login, refresh token y logout")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Registrar nuevo negocio", description = "Crea un tenant y su usuario OWNER. Devuelve tokens de autenticación.")
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Iniciar sesión")
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }

    @Operation(summary = "Renovar access token usando refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @Operation(summary = "Cerrar sesión y revocar refresh tokens")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Usuario usuario) {
        authService.logout(usuario);
        return ResponseEntity.noContent().build();
    }
}
