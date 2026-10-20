package com.zaga.licenciamientoapp.web.dto;

/** Vista administrativa de un activador y su equipo asignado (si lo tiene). */
public record ActivadorResponse(
        Long id,
        Long licenciaId,
        String estado,
        String fechaCreacion,
        String instalacionId,
        String huellaPcHash,
        String nombreEquipo,
        String sistemaOperativo,
        Integer versionHuella,
        String primeraActivacion,
        String ultimaVerificacion
) {
}
