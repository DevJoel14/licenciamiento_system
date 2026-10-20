package com.zaga.licenciamientoapp.service;

import com.zaga.licenciamientoapp.config.LicensingProperties;
import com.zaga.licenciamientoapp.domain.Activador;
import com.zaga.licenciamientoapp.domain.PcInfo;
import com.zaga.licenciamientoapp.service.fingerprint.HardwareFingerprintService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utilidades compartidas por la activacion (aplicacion) y la validacion del instalador
 * para resolver la huella de hardware y construir la asignacion {@code pc_info}, evitando
 * duplicar esa logica en cada servicio.
 */
@Component
public class PcIdentitySupport {

    private static final Pattern HEX64 = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final HardwareFingerprintService fingerprintService;
    private final LicensingProperties properties;

    public PcIdentitySupport(HardwareFingerprintService fingerprintService, LicensingProperties properties) {
        this.fingerprintService = fingerprintService;
        this.properties = properties;
    }

    /** Version actual del algoritmo de huella configurada en el servidor. */
    public int versionActual() {
        return properties.getFingerprint().getCurrentVersion();
    }

    /**
     * Resuelve la huella a partir de los componentes o de la huella ya calculada por el cliente.
     *
     * @return la huella en minusculas, o {@code null} si no se pudo resolver (el llamador decide el error).
     */
    public String resolverHuella(Integer versionHuella, List<String> componentes, String huellaPcHash) {

        int version = versionHuella != null ? versionHuella : properties.getFingerprint().getCurrentVersion();

        if (componentes != null && !componentes.isEmpty()) {
            if (!properties.getFingerprint().getSupportedVersions().contains(version)) {
                return null;
            }
            return fingerprintService.compute(version, componentes);
        }
        if (huellaPcHash != null && HEX64.matcher(huellaPcHash).matches()) {
            return huellaPcHash.toLowerCase();
        }

        return null;
    }

    /** Construye la asignacion {@code pc_info} con la identidad recibida. */
    public PcInfo nuevaPcInfo(Activador activador, String instalacionId, String nombreEquipo,
                              String sistemaOperativo, Integer versionHuella, String huella, LocalDateTime ahora) {
        PcInfo pc = new PcInfo();
        pc.setActivador(activador);
        pc.setInstalacionId(instalacionId);
        pc.setHuellaPcHash(huella);
        pc.setNombreEquipo(recortar(nombreEquipo, 128));
        pc.setSistemaOperativo(recortar(sistemaOperativo, 64));
        pc.setVersionHuella(versionHuella != null ? versionHuella : versionActual());
        pc.setPrimeraActivacion(ahora);
        pc.setUltimaVerificacion(ahora);
        pc.setUltimaModificacion(ahora);
        return pc;
    }

    /** Recorta un texto a la longitud maxima admitida por la columna. */
    public static String recortar(String valor, int max) {
        if (valor == null) {
            return null;
        }
        String v = valor.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }
}
