package com.tunegocio.turnosapi.entity;

/**
 * Estados del ciclo de vida de un turno.
 *
 * <p>Flujo principal:
 * <pre>
 *   PENDIENTE → CONFIRMADO → COMPLETADO
 *                   ↓
 *               CANCELADO
 *   CONFIRMADO → NO_SHOW
 * </pre>
 */
public enum TurnoStatus {

    /** Turno creado pero aún no confirmado por el profesional/negocio. */
    PENDIENTE,

    /** Turno confirmado explícitamente. */
    CONFIRMADO,

    /** Turno finalizado exitosamente. */
    COMPLETADO,

    /** Turno cancelado (libera el slot para nuevas reservas). */
    CANCELADO,

    /** El cliente no se presentó. */
    NO_SHOW
}
