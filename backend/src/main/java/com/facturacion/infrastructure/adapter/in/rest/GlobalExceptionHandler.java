package com.facturacion.infrastructure.adapter.in.rest;

import com.facturacion.domain.exception.DomainException;
import com.facturacion.domain.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

/** Traduce excepciones de dominio e infraestructura a respuestas HTTP coherentes. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(Instant timestamp, int status, String error, String message, List<String> details) {
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), List.of());
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> businessRule(DomainException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Datos invalidos", details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception e) {
        return build(HttpStatus.BAD_REQUEST, "Solicitud mal formada: revise los valores enviados", List.of());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> duplicate(DuplicateKeyException e) {
        return build(HttpStatus.CONFLICT, "Ya existe un registro con ese codigo", List.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> database(DataAccessException e) {
        log.error("Error de acceso a datos", e);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Base de datos no disponible: " + e.getMostSpecificCause().getMessage(), List.of());
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> backupEngine(RestClientException e) {
        log.warn("Motor de backup no disponible: {}", e.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Motor de backup (CouchDB en WSL) no disponible", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Error inesperado", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno: " + e.getMessage(), List.of());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, details));
    }
}
