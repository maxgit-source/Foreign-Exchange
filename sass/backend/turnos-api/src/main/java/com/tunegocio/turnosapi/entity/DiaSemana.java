package com.tunegocio.turnosapi.entity;

import java.time.DayOfWeek;

/**
 * Días de la semana en español para configurar disponibilidad de profesionales.
 * Mapea 1:1 con java.time.DayOfWeek (MONDAY=1 ... SUNDAY=7).
 */
public enum DiaSemana {

    LUNES,
    MARTES,
    MIERCOLES,
    JUEVES,
    VIERNES,
    SABADO,
    DOMINGO;

    /**
     * Convierte un DayOfWeek estándar de Java al enum en español.
     */
    public static DiaSemana from(DayOfWeek dayOfWeek) {
        return values()[dayOfWeek.getValue() - 1];
    }

    /**
     * Convierte al DayOfWeek estándar de Java.
     */
    public DayOfWeek toDayOfWeek() {
        return DayOfWeek.of(this.ordinal() + 1);
    }
}
