package com.tunegocio.turnosapi.entity;

import com.tunegocio.turnosapi.util.StringListConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * API Key de integración para un tenant (vive en schema global public).
 */
@Entity
@Table(
        schema = "public",
        name = "api_keys"
)
@Getter
@Setter
@NoArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "key_hash", nullable = false, length = 64, unique = true)
    private String keyHash;

    @Convert(converter = StringListConverter.class)
    @Column(name = "permisos", columnDefinition = "TEXT")
    private List<String> permisos = new ArrayList<>();

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "ultimo_uso")
    private LocalDateTime ultimoUso;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
