package com.zaga.licenciamientoapp.web;

import com.zaga.licenciamientoapp.service.offline.OfflineTokenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Expone la clave publica con la que el cliente verifica localmente el comprobante
 * de activacion (token offline). Es informacion publica.
 */
@RestController
public class PublicKeyController {

    private final OfflineTokenService offlineTokenService;

    public PublicKeyController(OfflineTokenService offlineTokenService) {
        this.offlineTokenService = offlineTokenService;
    }

    @GetMapping("/api/public-key")
    public Map<String, String> publicKey() {
        return Map.of("publicKey", offlineTokenService.publicKeyPem());
    }
}
