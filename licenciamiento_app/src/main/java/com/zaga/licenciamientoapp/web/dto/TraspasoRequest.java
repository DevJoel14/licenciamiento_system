package com.zaga.licenciamientoapp.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Autorizacion administrativa de un traslado (cambio de computadora). */
public record TraspasoRequest(
        @NotBlank(message = "autorizadoPor es obligatorio")
        String autorizadoPor
) {
}
