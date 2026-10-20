package com.zaga.licenciamientoapp.web.dto;

/** Resumen de licencia para las busquedas del panel administrativo. */
public record LicenciaResumenResponse(
        Long id,
        String codigoProducto,
        String clienteRef,
        String estado,
        String fechaCreacion,
        long totalSeriales
) {
}
