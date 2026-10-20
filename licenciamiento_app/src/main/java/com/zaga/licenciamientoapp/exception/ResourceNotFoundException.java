package com.zaga.licenciamientoapp.exception;

/** Recurso administrativo no encontrado (licencia o activador inexistente). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String mensaje) {
        super(mensaje);
    }
}
