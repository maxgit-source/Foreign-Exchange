package com.tunegocio.turnosapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunegocio.turnosapi.dto.AuthResponseDTO;
import com.tunegocio.turnosapi.dto.LoginRequest;
import com.tunegocio.turnosapi.dto.RefreshTokenRequest;
import com.tunegocio.turnosapi.dto.RegisterRequest;
import com.tunegocio.turnosapi.entity.Role;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.exception.GlobalExceptionHandler;
import com.tunegocio.turnosapi.exception.UnauthorizedException;
import com.tunegocio.turnosapi.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        AuthController controller = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_debeResponder201ConPayload() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Owner");
        request.setEmail("owner@test.com");
        request.setPassword("password123");
        request.setTenantNombre("Negocio");
        request.setTenantEmail("negocio@test.com");

        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tenantSlug").value("negocio"));
    }

    @Test
    void login_debeResponder200ConPayload() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("owner@test.com");
        request.setPassword("password123");

        when(authService.authenticate(any(LoginRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void refresh_debeResponder200ConPayload() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh-token");

        when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void logout_debeResponder204CuandoHayUsuarioAutenticado() throws Exception {
        authenticateAs(usuario());

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());

        verify(authService).logout(any(Usuario.class));
    }

    @Test
    void logout_debeResponder401CuandoNoHayUsuarioAutenticado() throws Exception {
        doThrow(new UnauthorizedException("Debe autenticarse para cerrar sesión"))
                .when(authService).logout(null);

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Debe autenticarse para cerrar sesión"));
    }

    private void authenticateAs(Usuario usuario) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities())
        );
    }

    private Usuario usuario() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setNombre("Negocio");
        tenant.setSlug("negocio");

        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNombre("Owner");
        usuario.setEmail("owner@test.com");
        usuario.setRole(Role.OWNER);
        usuario.setTenant(tenant);
        usuario.setEnabled(true);
        return usuario;
    }

    private AuthResponseDTO authResponse() {
        return AuthResponseDTO.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600)
                .usuarioId(1L)
                .nombre("Owner")
                .email("owner@test.com")
                .role("OWNER")
                .tenantId(1L)
                .tenantNombre("Negocio")
                .tenantSlug("negocio")
                .build();
    }
}
