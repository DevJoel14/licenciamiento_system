package com.zaga.licenciamientoapp.web.dto;

import java.util.Map;

/** Estado agregado de una licencia y sus activadores por estado. */
public record EstadoLicenciaResponse(
        boolean exitoso,
        Long licenciaId,
        String codigoProducto,
        String clienteRef,
        String estado,
        long totalSeriales,
        Map<String, Long> serialesPorEstado
) {
}
