package com.zaga.licenciamientoapp.domain;

/**
 * Estado de un {@link Activador} (serial individual).
 *
 * <ul>
 *   <li>{@code SIN_USO}: serial generado y disponible, sin equipo asignado.</li>
 *   <li>{@code OCUPADO}: serial asociado a una computadora autorizada.</li>
 *   <li>{@code TRASPASO}: el administrador autorizo cambiar la computadora asignada.</li>
 *   <li>{@code REVOCADO}: serial permanentemente rechazado para nuevas activaciones.</li>
 * </ul>
 */
public enum EstadoActivador {

    SIN_USO,
    OCUPADO,
    TRASPASO,
    REVOCADO

}
