package com.zaga.licenciamientoapp.web.dto;

/**
 * Respuesta de activacion/verificacion. {@code exitoso=false} indica un rechazo de
 * negocio (no un error interno del servidor).
 */
public record ActivacionResponse(
        boolean exitoso,
        String codigo,
        String mensaje,
        String estadoActivador,
        String instalacionId,
        String huellaPcHash,
        String primeraActivacion,
        String ultimaVerificacion,
        String tokenOffline
) {
}
