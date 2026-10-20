package com.zaga.licenciamientoapp.web;

import com.zaga.licenciamientoapp.config.LicensingProperties;
import com.zaga.licenciamientoapp.service.InstallerValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint de validacion previa para Advanced Installer.
 * <p>
 * Devuelve TEXTO PLANO con el codigo configurado en el instalador:
 * {@code 601} (permitir) o {@code 602} (rechazar). No son codigos HTTP estandar.
 * <p>
 * Es idempotente: no registra computadoras, no cambia estados ni consume puestos.
 * Acepta los parametros por query string o por formulario.
 * <p>
 * Advanced Installer (validacion server-side) envia por POST los campos {@code sn}
 * (serial) y {@code ai} (informacion adicional, usada aqui para el codigo de producto).
 * Por compatibilidad tambien se aceptan {@code serial}/{@code producto}.
 * Respuesta: {@code 601} para permitir; {@code 602\nMensaje} para rechazar.
 */
@RestController
public class InstallerController {

    private static final Logger log = LoggerFactory.getLogger(InstallerController.class);

    private final InstallerValidationService validationService;
    private final LicensingProperties properties;

    public InstallerController(InstallerValidationService validationService, LicensingProperties properties) {
        this.validationService = validationService;
        this.properties = properties;
    }

    @PostMapping(value = "/api/instalador/validar", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String validarPost(@RequestParam Map<String, String> params) {
        return validar(params);
    }

    @GetMapping(value = "/api/instalador/validar", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String validarGet(@RequestParam Map<String, String> params) {
        return validar(params);
    }

    private String validar(Map<String, String> params) {

        // "sn" y "ai" son los nombres que usa Advanced Installer en su validacion server-side.
        String serial = first(params, "sn", "serial", "Serial", "SERIAL");
        String producto = first(params, "ai", "producto", "codigoProducto", "codigo_producto", "product", "Product");

        InstallerValidationService.Resultado resultado = validationService.validar(serial, producto);

        if (resultado.permitido()) {
            log.info("Validacion de instalador PERMITIDA producto={}", producto);
            return properties.getInstaller().getAllowCode();
        }

        log.info("Validacion de instalador RECHAZADA producto={} motivo={}",
                producto, resultado.motivo() != null ? resultado.motivo().getCodigo() : "N/A");

        // Formato de Advanced Installer: "AnswerCode\nMessage" (el mensaje solo se muestra si != 601).
        String mensaje = resultado.motivo() != null ? resultado.motivo().getMensaje() : "";

        return properties.getInstaller().getDenyCode() + "\n" + mensaje;

    }

    private static String first(Map<String, String> params, String... claves) {

        for (String clave : claves) {

            String v = params.get(clave);
            if (v != null && !v.isBlank()) {
                return v;
            }

        }

        return null;

    }

}
