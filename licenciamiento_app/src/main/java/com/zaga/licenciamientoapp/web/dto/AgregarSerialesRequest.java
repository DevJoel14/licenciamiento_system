package com.zaga.licenciamientoapp.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Agregar seriales (puestos adicionales) a una licencia existente. */
public record AgregarSerialesRequest(
        @NotNull(message = "cantidad es obligatoria")
        @Min(value = 1, message = "cantidad debe ser al menos 1")
        Integer cantidad
) {
}
