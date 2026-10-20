package com.zaga.licenciamientoapp.service.serial;

import com.zaga.licenciamientoapp.config.LicensingProperties;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Generador de seriales basado en {@link SecureRandom}. El alfabeto por defecto
 * omite caracteres ambiguos (0/O, 1/I/L) para facilitar la transcripcion manual.
 */
@Service
public class SecureSerialGenerator implements SerialGenerator {

    private final SecureRandom random = new SecureRandom();
    private final char[] alphabet;
    private final int groups;
    private final int groupLength;

    public SecureSerialGenerator(LicensingProperties properties) {
        LicensingProperties.Serial cfg = properties.getSerial();
        String alpha = cfg.getAlphabet();
        if (alpha == null || alpha.length() < 16) {
            throw new IllegalStateException("licensing.serial.alphabet debe tener al menos 16 caracteres.");
        }
        if (cfg.getGroups() < 1 || cfg.getGroupLength() < 3) {
            throw new IllegalStateException("Configuracion de serial invalida (groups>=1, group-length>=3).");
        }
        this.alphabet = alpha.toCharArray();
        this.groups = cfg.getGroups();
        this.groupLength = cfg.getGroupLength();
    }

    @Override
    public String generar() {
        StringBuilder sb = new StringBuilder(groups * (groupLength + 1));
        for (int g = 0; g < groups; g++) {
            if (g > 0) {
                sb.append('-');
            }
            for (int i = 0; i < groupLength; i++) {
                sb.append(alphabet[random.nextInt(alphabet.length)]);
            }
        }
        return sb.toString();
    }
}
