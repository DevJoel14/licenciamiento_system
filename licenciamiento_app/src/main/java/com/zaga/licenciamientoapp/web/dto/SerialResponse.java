package com.zaga.licenciamientoapp.web.dto;

/**
 * Serial recien generado. El valor en claro se devuelve UNA SOLA VEZ al administrador;
 * luego solo existe su hash HMAC en la base de datos.
 */
public record SerialResponse(
        Long activadorId,
        String serial,
        String estado,
        String fechaCreacion
) {
}
