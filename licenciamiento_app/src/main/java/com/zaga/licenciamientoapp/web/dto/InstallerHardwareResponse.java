package com.zaga.licenciamientoapp.web.dto;

/**
 * Respuesta del endpoint de validacion con hardware para el custom action del instalador.
 * {@code permitido=true} equivale al codigo de permitir (601); {@code false} al de rechazar (602).
 */
public record InstallerHardwareResponse(
        boolean permitido,
        String codigo,
        String motivo,
        String mensaje
) {
}
