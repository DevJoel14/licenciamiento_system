package com.zaga.licenciamientoapp.service.fingerprint;

import java.util.List;

/**
 * Interfaz extensible de identificacion de computadora.
 *
 * El cliente recopila los identificadores de hardware admitidos por la politica de
 * privacidad del producto (nunca el numero de serie del disco) y los envia al backend.
 * El servidor calcula la huella mediante un algoritmo versionado.
 *
 * Nuevas versiones del algoritmo se agregan implementando esta interfaz y declarando
 * la version en {@code licensing.fingerprint.supported-versions}.
 */
public interface HardwareFingerprintService {

    /**
     * Calcula una huella estable e independiente del orden de los componentes.
     *
     * @param version     version del algoritmo solicitada por el cliente
     * @param componentes identificadores de hardware (sin numero de serie de disco)
     * @return hash hexadecimal de 64 caracteres
     */
    String compute(int version, List<String> componentes);
}
