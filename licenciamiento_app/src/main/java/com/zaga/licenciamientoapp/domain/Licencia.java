package com.zaga.licenciamientoapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Licencia comercial correspondiente a un producto.
 * Una licencia puede tener varios activadores (seriales / puestos).
 */
@Entity
@Table(name = "licencias")
@Getter
@Setter
@NoArgsConstructor
public class Licencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador logico del producto, p.ej. SISTEMA_TALLER. */
    @Column(name = "codigo_producto", nullable = false, length = 64)
    private String codigoProducto;

    /** Referencia del comprador (cliente). */
    @Column(name = "cliente_ref", nullable = false, length = 128)
    private String clienteRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoLicencia estado = EstadoLicencia.ACTIVA;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    public Licencia(String codigoProducto, String clienteRef) {
        this.codigoProducto = codigoProducto;
        this.clienteRef = clienteRef;
        this.estado = EstadoLicencia.ACTIVA;
        this.fechaCreacion = LocalDateTime.now();
    }
}
