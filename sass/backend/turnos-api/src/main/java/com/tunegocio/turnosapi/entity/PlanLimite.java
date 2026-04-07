package com.tunegocio.turnosapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(schema = "public", name = "plan_limites")
@Getter
@Setter
@NoArgsConstructor
public class PlanLimite {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Plan plan;

    @Column(name = "max_profesionales", nullable = false)
    private Integer maxProfesionales;

    @Column(name = "max_servicios", nullable = false)
    private Integer maxServicios;

    @Column(name = "max_turnos_mes", nullable = false)
    private Integer maxTurnosMes;

    @Column(name = "max_clientes", nullable = false)
    private Integer maxClientes;

    @Column(name = "max_productos", nullable = false)
    private Integer maxProductos;

    @Column(name = "tiene_ecommerce", nullable = false)
    private boolean tieneEcommerce;

    @Column(name = "tiene_reportes", nullable = false)
    private boolean tieneReportes;

    @Column(name = "tiene_api_publica", nullable = false)
    private boolean tieneApiPublica;

    @Column(name = "tiene_whatsapp", nullable = false)
    private boolean tieneWhatsapp;

    @Column(name = "precio_mensual", precision = 10, scale = 2)
    private BigDecimal precioMensual;
}
