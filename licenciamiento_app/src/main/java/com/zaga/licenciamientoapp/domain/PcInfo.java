package com.zaga.licenciamientoapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Asignacion actual de un serial a una computadora. Relacion 1:1 con
 * {@link Activador}. Durante un traslado autorizado se actualiza la fila existente.
 */
@Entity
@Table(name = "pc_info")
@Getter
@Setter
@NoArgsConstructor
public class PcInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activador_id", nullable = false, unique = true)
    private Activador activador;

    /** UUID persistente de la instalacion generado por el cliente. */
    @Column(name = "instalacion_id", nullable = false, length = 64)
    private String instalacionId;

    /** Hash de la huella de hardware (algoritmo versionado). */
    @Column(name = "huella_pc_hash", nullable = false, length = 64)
    private String huellaPcHash;

    @Column(name = "nombre_equipo", length = 128)
    private String nombreEquipo;

    @Column(name = "sistema_operativo", length = 64)
    private String sistemaOperativo;

    @Column(name = "version_huella", nullable = false)
    private int versionHuella = 1;

    @Column(name = "primera_activacion", nullable = false)
    private LocalDateTime primeraActivacion;

    @Column(name = "ultima_verificacion")
    private LocalDateTime ultimaVerificacion;

    @Column(name = "ultima_modificacion", nullable = false)
    private LocalDateTime ultimaModificacion;

}
