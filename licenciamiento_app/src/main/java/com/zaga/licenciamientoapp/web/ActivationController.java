package com.zaga.licenciamientoapp.web;

import com.zaga.licenciamientoapp.service.ActivationService;
import com.zaga.licenciamientoapp.web.dto.ActivacionResponse;
import com.zaga.licenciamientoapp.web.dto.InstallerHardwareRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de activacion y verificacion para la aplicacion de escritorio.
 * Independientes del lenguaje/framework del cliente (solo HTTP/JSON sobre HTTPS).
 */
@RestController
@RequestMapping("/api/activacion")
public class ActivationController {

    private final ActivationService activationService;

    public ActivationController(ActivationService activationService) {
        this.activationService = activationService;
    }

    /** Activa (o completa un traslado) el serial en la computadora actual. */
    @PostMapping("/activar")
    public ActivacionResponse activar(@Valid @RequestBody InstallerHardwareRequest request) {
        return activationService.activar(request);
    }

    /** Verifica una activacion existente sin crear asignaciones nuevas. */
    @PostMapping("/verificar")
    public ActivacionResponse verificar(@Valid @RequestBody InstallerHardwareRequest request) {
        return activationService.verificar(request);
    }
}
