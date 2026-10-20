package com.zaga.licenciamientoapp.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepcion de negocio. Se distingue de un error interno para responder
 * "licencia rechazada" de forma controlada y con un codigo de motivo estable.
 */
public class LicensingException extends RuntimeException {

    private final MotivoRechazo motivo;
    private final HttpStatus httpStatus;

    public LicensingException(MotivoRechazo motivo) {
        this(motivo, HttpStatus.CONFLICT);
    }

    public LicensingException(MotivoRechazo motivo, HttpStatus httpStatus) {
        super(motivo.getMensaje());
        this.motivo = motivo;
        this.httpStatus = httpStatus;
    }

    public MotivoRechazo getMotivo() {
        return motivo;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
