package com.tunegocio.turnosapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class TurnoResponseDTO {

    private Long   id;

    // ─── Cliente ──────────────────────────────────────────────────────────────
    private Long   clienteId;
    private String nombreCliente;
    private String emailCliente;
    private String telefonoCliente;

    // ─── Profesional ──────────────────────────────────────────────────────────
    private Long   profesionalId;
    private String nombreProfesional;

    // ─── Servicio ─────────────────────────────────────────────────────────────
    private Long       servicioId;
    private String     nombreServicio;
    private Integer    duracionMinutos;
    private BigDecimal precioServicio;
    private List<Long> servicioIds;
    private List<TurnoServicioDTO> servicios;
    private Integer duracionTotalMinutos;
    private BigDecimal precioTotal;

    // ─── Turno ────────────────────────────────────────────────────────────────
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime fechaHoraInicio;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime fechaHoraFin;

    private String estado;
    private String notas;
    private Long turnoPadreId;
    private boolean recurrente;
    private Integer recurrenciaSemanas;
    private String timezone;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
