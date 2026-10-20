package com.zaga.licenciamientoapp.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Solicitud JSON con el serial y la identidad de hardware de la computadora.
 * La usan tanto la activacion/verificacion de la aplicacion de escritorio como el
 * custom action del instalador (endpoint {@code /api/instalador/validar-hardware}).
 */
public record InstallerHardwareRequest(

        @NotBlank(message = "serial es obligatorio")
        String serial,

        @NotBlank(message = "codigoProducto es obligatorio")
        String codigoProducto,

        @NotBlank(message = "instalacionId es obligatorio")
        String instalacionId,

        /** Huella ya calculada por el cliente (hex de 64). */
        String huellaPcHash,

        /** Componentes de hardware; el servidor calcula la huella. */
        List<String> componentesHardware,

        /** Version del algoritmo de huella (por defecto la actual del servidor). */
        Integer versionHuella,

        String nombreEquipo,
        String sistemaOperativo
) {
}
