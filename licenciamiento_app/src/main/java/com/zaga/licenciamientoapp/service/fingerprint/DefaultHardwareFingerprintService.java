package com.zaga.licenciamientoapp.service.fingerprint;

import com.zaga.licenciamientoapp.service.serial.HmacSha256Service;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Implementacion por defecto (version 1) de la huella de hardware.
 *
 * Canonicaliza los componentes (recorta, minusculas, ignora vacios, ordena para ser
 * independiente del orden de envio) y calcula SHA-256 sobre {@code "v<version>|<c1>|<c2>|..."}.
 */
@Service
public class DefaultHardwareFingerprintService implements HardwareFingerprintService {

    private static final int VERSION = 1;

    @Override
    public String compute(int version, List<String> componentes) {
        if (version != VERSION) {
            throw new IllegalArgumentException("Version de huella no soportada: " + version);
        }
        if (componentes == null) {
            throw new IllegalArgumentException("La lista de componentes de hardware es obligatoria.");
        }
        TreeSet<String> normalizados = new TreeSet<>();
        for (String c : componentes) {
            if (c == null) {
                continue;
            }
            String v = c.trim().toLowerCase();
            if (!v.isEmpty()) {
                normalizados.add(v);
            }
        }
        // Se exige al menos dos componentes para no depender de un unico dato debil.
        if (normalizados.size() < 2) {
            throw new IllegalArgumentException(
                    "Se requieren al menos dos componentes de hardware validos para calcular la huella.");
        }
        List<String> ordenados = new ArrayList<>(normalizados);
        String payload = "v" + version + "|" + String.join("|", ordenados);
        return HmacSha256Service.sha256Hex(Objects.requireNonNull(payload));
    }
}
