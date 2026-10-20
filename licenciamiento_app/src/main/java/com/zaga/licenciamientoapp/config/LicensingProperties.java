package com.zaga.licenciamientoapp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Propiedades del sistema de licenciamiento (prefijo {@code licensing.*}).
 * Las credenciales se inyectan desde variables de entorno en produccion.
 */
@Component
@ConfigurationProperties(prefix = "licensing")
@Getter
@Setter
public class LicensingProperties {

    /** Secreto HMAC-SHA-256 para indexar seriales sin guardarlos en claro. */
    private String hmacSecret;

    /** Clave para autenticar operaciones administrativas (cabecera X-Admin-Api-Key). */
    private String adminApiKey;

    private Serial serial = new Serial();
    private Fingerprint fingerprint = new Fingerprint();
    private Installer installer = new Installer();
    private Offline offline = new Offline();
    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class Serial {
        private int groups = 5;
        private int groupLength = 5;
        private String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    }

    @Getter
    @Setter
    public static class Fingerprint {
        private int currentVersion = 1;
        private List<Integer> supportedVersions = List.of(1);
    }

    @Getter
    @Setter
    public static class Installer {
        private String allowCode = "601";
        private String denyCode = "602";
    }

    @Getter
    @Setter
    public static class Offline {
        private boolean enabled = true;
        private int graceDays = 7;
        private String privateKeyPath = "keys/offline_private.pem";
        private String publicKeyPath = "keys/offline_public.pem";
    }

    @Getter
    @Setter
    public static class RateLimit {
        private boolean enabled = true;
        private int capacity = 60;
        private int refillSeconds = 60;
    }
}
