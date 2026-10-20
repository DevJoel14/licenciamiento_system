package com.zaga.licenciamientoapp.web;

import com.zaga.licenciamientoapp.exception.LicensingException;
import com.zaga.licenciamientoapp.exception.ResourceNotFoundException;
import com.zaga.licenciamientoapp.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Traduce excepciones a respuestas uniformes y, sobre todo, separa el rechazo de
 * negocio (licencia/serial) del error interno del servidor.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LicensingException.class)
    public ResponseEntity<ErrorResponse> handleLicensing(LicensingException ex) {
        log.info("Rechazo de negocio: {}", ex.getMotivo().getCodigo());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ErrorResponse.negocio(ex.getMotivo().getCodigo(), ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.negocio("NO_ENCONTRADO", ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex) {
        // Recursos estaticos ausentes (p. ej. /favicon.ico): 404 silencioso, sin log de error.
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MissingServletRequestParameterException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleValidation(Exception ex) {
        String mensaje = ex.getMessage();
        if (ex instanceof MethodArgumentNotValidException manv) {
            mensaje = manv.getBindingResult().getFieldErrors().stream()
                    .map(e -> e.getField() + ": " + e.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.validacion(mensaje));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        // Cuerpo ausente, JSON mal formado o tipos incompatibles: es un error del cliente (400), no del servidor.
        log.info("Solicitud con cuerpo ilegible o mal formado");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.validacion("El cuerpo de la solicitud es invalido o no tiene un formato JSON correcto."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternal(Exception ex) {
        log.error("Error interno no controlado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.interno("Ocurrio un error interno. Contacte al administrador."));
    }
}
