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
 * Webhook registrado por un tenant para recibir eventos de la plataforma.
 */
@Entity
@Table(
        schema = "public",
        name = "webhooks"
)
@Getter
@Setter
@NoArgsConstructor
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Convert(converter = StringListConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> eventos = new ArrayList<>();

    @Column(name = "secret_hash", length = 64)
    private String secretHash;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
