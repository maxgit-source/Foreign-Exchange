package com.tunegocio.turnosapi.entity;

/**
 * Planes de suscripción disponibles en la plataforma.
 * Cada plan desbloquea un conjunto de funcionalidades y tiene límites distintos.
 */
public enum Plan {

    /** Hasta 1 profesional, 50 turnos/mes, sin booking público. */
    FREE,

    /** Hasta 5 profesionales, 500 turnos/mes, booking público habilitado. */
    BASIC,

    /** Profesionales ilimitados, turnos ilimitados, todas las funcionalidades. */
    PRO,

    /** Configuración personalizada para grandes organizaciones. */
    ENTERPRISE
}
