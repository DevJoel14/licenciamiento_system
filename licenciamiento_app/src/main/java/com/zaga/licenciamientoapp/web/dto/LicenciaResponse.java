package com.zaga.licenciamientoapp.web.dto;

import java.util.List;

/** Representacion administrativa de una licencia con sus seriales registrados. */
public record LicenciaResponse(
        Long id,
        String codigoProducto,
        String clienteRef,
        String estado,
        String fechaCreacion,
        List<SerialResponse> seriales
) {
}
