package com.zaga.licenciamientoapp.service.serial;

import com.zaga.licenciamientoapp.config.LicensingProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Implementacion de {@link HmacService} basada en HMAC-SHA-256.
 * El secreto proviene de la configuracion ({@code licensing.hmac-secret}).
 */
@Service
public class HmacSha256Service implements HmacService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final byte[] secretKey;

    public HmacSha256Service(LicensingProperties properties) {
        String secret = properties.getHmacSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "licensing.hmac-secret no esta configurado; defina LICENSE_HMAC_SECRET en el entorno.");
        }
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String normalize(String serial) {
        if (serial == null) {
            return "";
        }
        // Se eliminan guiones, espacios y cualquier caracter no alfanumerico.
        return serial.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    @Override
    public String serialToHash(String serial) {
        String normalized = normalize(serial);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("El serial no puede estar vacio.");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, ALGORITHM));
            byte[] raw = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return toHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible calcular el HMAC del serial.", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    /** Utilidad para hashear datos arbitrarios (usada por la huella de hardware). */
    public static String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible calcular SHA-256.", e);
        }
    }
}
