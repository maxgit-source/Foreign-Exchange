package com.tunegocio.turnosapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción lanzada cuando una operación viola una regla de negocio
 * de integridad o conflicto de estado.
 * Produce una respuesta HTTP 409 Conflict.
 *
 * <p>Ejemplos de uso:
 * <ul>
 *   <li>Email ya registrado al intentar crear un usuario</li>
 *   <li>Turno que se solapa con uno existente</li>
 *   <li>Slug de tenant ya en uso</li>
 * </ul>
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
