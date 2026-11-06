package com.facturacion.domain.exception;

/**
 * Violacion de una regla de negocio. Pertenece al nucleo: no depende de ningun framework.
 */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
