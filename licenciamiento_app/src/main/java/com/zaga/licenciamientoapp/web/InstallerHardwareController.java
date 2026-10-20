package com.zaga.licenciamientoapp.web;

import com.zaga.licenciamientoapp.config.LicensingProperties;
import com.zaga.licenciamientoapp.exception.MotivoRechazo;
import com.zaga.licenciamientoapp.service.InstallerValidationService;
import com.zaga.licenciamientoapp.service.serial.HmacService;
import com.zaga.licenciamientoapp.web.dto.InstallerHardwareRequest;
import com.zaga.licenciamientoapp.web.dto.InstallerHardwareResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de validacion del instalador con identidad de equipo (JSON), pensado para un
 * custom action de Advanced Installer. Complementa a la validacion server-side nativa:
 * aqui si se envia la huella de hardware y se comprueba que la instalacion ocurre en el
 * mismo equipo registrado (misma PC permite, otra PC rechaza).
 * <p>
 * Al validar, registra el uso del serial: en un serial {@code SIN_USO} crea la fila de
 * {@code pc_info} y pasa el activador a {@code OCUPADO}; en {@code OCUPADO} con la misma
 * huella permite; con huella distinta rechaza.
 */
@RestController
public class InstallerHardwareController {

    private static final Logger log = LoggerFactory.getLogger(InstallerHardwareController.class);

    private final InstallerValidationService validationService;
    private final LicensingProperties properties;
    private final HmacService hmacService;

    public InstallerHardwareController(InstallerValidationService validationService,
                                       LicensingProperties properties,
                                       HmacService hmacService) {
        this.validationService = validationService;
        this.properties = properties;
        this.hmacService = hmacService;
    }

    @PostMapping(value = "/api/instalador/validar-hardware", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public InstallerHardwareResponse validarHardware(@RequestBody(required = false) InstallerHardwareRequest request) {

        InstallerValidationService.Resultado resultado = validationService.validarConHardware(request);

        log.info("Validacion instalador/hardware producto={} serial={} componentes={} -> {}",
                request != null ? request.codigoProducto() : null,
                hmacService.mask(request != null ? request.serial() : null),
                request != null && request.componentesHardware() != null ? request.componentesHardware().get(1) : 0,
                resultado.permitido() ? "PERMITIDO"
                        : (resultado.motivo() != null ? resultado.motivo().getCodigo() : "N/A"));

        if (resultado.permitido()) {
            return new InstallerHardwareResponse(
                    true, properties.getInstaller().getAllowCode(), null, "Equipo autorizado.");
        }

        MotivoRechazo motivo = resultado.motivo() != null ? resultado.motivo() : MotivoRechazo.DATOS_INVALIDOS;
        return new InstallerHardwareResponse(
                false, properties.getInstaller().getDenyCode(), motivo.getCodigo(), motivo.getMensaje());

    }

}
