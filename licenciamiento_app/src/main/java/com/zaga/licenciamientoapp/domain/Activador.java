package com.zaga.licenciamientoapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Un serial individual de una licencia. El serial original no se guarda:
 * unicamente su hash HMAC-SHA-256 en {@code serial_hash}.
 */
@Entity
@Table(name = "activadores")
@Getter
@Setter
@NoArgsConstructor
public class Activador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "licencia_id", nullable = false)
    private Licencia licencia;

    /** HMAC-SHA-256 (hex, 64 chars) del serial normalizado. UNIQUE. */
    @Column(name = "serial_hash", nullable = false, length = 64, unique = true)
    private String serialHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoActivador estado = EstadoActivador.SIN_USO;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    public Activador(Licencia licencia, String serialHash) {
        this.licencia = licencia;
        this.serialHash = serialHash;
        this.estado = EstadoActivador.SIN_USO;
        this.fechaCreacion = LocalDateTime.now();
    }
}
