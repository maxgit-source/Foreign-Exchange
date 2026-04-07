package com.tunegocio.turnosapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción lanzada cuando un recurso solicitado no existe en la base de datos.
 * Produce una respuesta HTTP 404 Not Found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, Long id) {
        super(String.format("%s con id %d no encontrado", resource, id));
    }

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s con %s '%s' no encontrado", resource, field, value));
    }
}
