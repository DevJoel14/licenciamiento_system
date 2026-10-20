package com.zaga.licenciamientoapp.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Creacion de una licencia y, opcionalmente, de sus primeros seriales. */
public record CrearLicenciaRequest(

        @NotBlank(message = "codigoProducto es obligatorio")
        String codigoProducto,

        @NotBlank(message = "clienteRef es obligatorio")
        String clienteRef,

        @Min(value = 0, message = "cantidadSeriales no puede ser negativa")
        Integer cantidadSeriales
) {
    public int serialesSolicitados() {
        return cantidadSeriales == null ? 0 : cantidadSeriales;
    }
}
