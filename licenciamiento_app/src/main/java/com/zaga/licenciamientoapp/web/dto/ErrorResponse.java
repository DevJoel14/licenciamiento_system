package com.zaga.licenciamientoapp.web.dto;

/**
 * Respuesta de error uniforme. Se distingue {@code codigo} de negocio del error
 * interno mediante {@code tipo}.
 */
public record ErrorResponse(boolean exitoso, String tipo, String codigo, String mensaje) {

    public static ErrorResponse negocio(String codigo, String mensaje) {
        return new ErrorResponse(false, "NEGOCIO", codigo, mensaje);
    }

    public static ErrorResponse interno(String mensaje) {
        return new ErrorResponse(false, "INTERNO", "ERROR_INTERNO", mensaje);
    }

    public static ErrorResponse validacion(String mensaje) {
        return new ErrorResponse(false, "VALIDACION", "DATOS_INVALIDOS", mensaje);
    }
}
