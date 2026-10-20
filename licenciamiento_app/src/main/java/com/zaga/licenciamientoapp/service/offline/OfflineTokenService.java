package com.zaga.licenciamientoapp.service.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaga.licenciamientoapp.config.LicensingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emite un comprobante de activacion firmado (RSA SHA-256) que el cliente puede
 * verificar localmente con la clave publica para operar sin conexion durante la
 * ventana de gracia configurada. NO sustituye la verificacion en linea: es una
 * autorizacion local acotada en el tiempo.
 */
@Service
public class OfflineTokenService {

    private static final Logger log = LoggerFactory.getLogger(OfflineTokenService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final LicensingProperties properties;
    private final ObjectMapper objectMapper;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    public OfflineTokenService(LicensingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        if (properties.getOffline().isEnabled()) {
            initKeys();
        }
    }

    public boolean isEnabled() {
        return properties.getOffline().isEnabled();
    }

    /**
     * Emite un token firmado con la identidad autorizada y una expiracion.
     *
     * @return token en formato {@code base64url(payload).base64url(firma)} o {@code null}
     * si la autorizacion offline esta deshabilitada.
     */
    public String emitir(String codigoProducto, String instalacionId, String huellaPcHash,
                         String serialEnmascarado, LocalDateTime emitidoEn) {
        if (!isEnabled()) {
            return null;
        }
        LocalDateTime expira = emitidoEn.plusDays(properties.getOffline().getGraceDays());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("codigoProducto", codigoProducto);
        payload.put("instalacionId", instalacionId);
        payload.put("huellaPcHash", huellaPcHash);
        payload.put("serial", serialEnmascarado);
        payload.put("emitidoEn", emitidoEn.format(TS));
        payload.put("expiraEn", expira.format(TS));
        try {
            byte[] payloadBytes = objectMapper.writeValueAsBytes(payload);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(payloadBytes);
            byte[] firma = signer.sign();
            return B64.encodeToString(payloadBytes) + "." + B64.encodeToString(firma);
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible emitir el token offline.", e);
        }
    }

    public String publicKeyPem() {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    // --------------------------------------------------------------- claves

    private void initKeys() {
        try {
            File priv = new File(properties.getOffline().getPrivateKeyPath());
            File pub = new File(properties.getOffline().getPublicKeyPath());
            if (priv.exists() && pub.exists()) {
                loadKeys(priv.toPath(), pub.toPath());
            } else {
                generateAndSave(priv.toPath(), pub.toPath());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error inicializando llaves de firma offline.", e);
        }
    }

    private void generateAndSave(Path privPath, Path pubPath) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        this.privateKey = pair.getPrivate();
        this.publicKey = pair.getPublic();

        if (privPath.getParent() != null) {
            Files.createDirectories(privPath.getParent());
        }
        Files.writeString(privPath, pem("PRIVATE KEY", privateKey.getEncoded()), StandardCharsets.UTF_8);
        Files.writeString(pubPath, pem("PUBLIC KEY", publicKey.getEncoded()), StandardCharsets.UTF_8);
        log.info("Par RSA de firma offline generado en {}", privPath.toAbsolutePath());
    }

    private void loadKeys(Path privPath, Path pubPath) throws Exception {
        String privPem = Files.readString(privPath, StandardCharsets.UTF_8);
        byte[] privBytes = Base64.getDecoder().decode(strip(privPem, "PRIVATE KEY"));
        this.privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privBytes));

        String pubPem = Files.readString(pubPath, StandardCharsets.UTF_8);
        byte[] pubBytes = Base64.getDecoder().decode(strip(pubPem, "PUBLIC KEY"));
        this.publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubBytes));
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
    }

    private static String strip(String pem, String type) {
        return pem.replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
    }
}
