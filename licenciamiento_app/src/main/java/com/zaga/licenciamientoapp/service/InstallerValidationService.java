package com.zaga.licenciamientoapp.service;

import com.zaga.licenciamientoapp.domain.Activador;
import com.zaga.licenciamientoapp.domain.EstadoActivador;
import com.zaga.licenciamientoapp.domain.EstadoLicencia;
import com.zaga.licenciamientoapp.domain.Licencia;
import com.zaga.licenciamientoapp.domain.PcInfo;
import com.zaga.licenciamientoapp.exception.MotivoRechazo;
import com.zaga.licenciamientoapp.repository.ActivadorRepository;
import com.zaga.licenciamientoapp.repository.PcInfoRepository;
import com.zaga.licenciamientoapp.service.serial.HmacService;
import com.zaga.licenciamientoapp.web.dto.InstallerHardwareRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Validacion previa realizada por Advanced Installer.
 *
 * Existen dos variantes:
 * <ul>
 *   <li>{@link #validar(String, String)}: server-side nativa (serial + producto). Es de solo
 *       lectura: no registra computadoras ni cambia estados.</li>
 *   <li>{@link #validarConHardware(InstallerHardwareRequest)}: ademas de validar serial y
 *       producto, comprueba la identidad de equipo comparando la huella de hardware con
 *       {@code pc_info} y, si el equipo esta autorizado, registra el uso: crea la fila de
 *       {@code pc_info} y pasa el activador de {@code SIN_USO} a {@code OCUPADO}.</li>
 * </ul>
 */
@Service
public class InstallerValidationService {

    private final ActivadorRepository activadorRepository;
    private final PcInfoRepository pcInfoRepository;
    private final HmacService hmacService;
    private final PcIdentitySupport identity;

    public InstallerValidationService(ActivadorRepository activadorRepository,
                                      PcInfoRepository pcInfoRepository,
                                      HmacService hmacService,
                                      PcIdentitySupport identity) {
        this.activadorRepository = activadorRepository;
        this.pcInfoRepository = pcInfoRepository;
        this.hmacService = hmacService;
        this.identity = identity;
    }

    public record Resultado(boolean permitido, MotivoRechazo motivo) {
        public static Resultado ok() {
            return new Resultado(true, null);
        }

        public static Resultado rechazo(MotivoRechazo motivo) {
            return new Resultado(false, motivo);
        }
    }

    /**
     * Validacion server-side nativa: serial + codigo de producto. No requiere hardware.
     */
    @Transactional(readOnly = true)
    public Resultado validar(String serial, String codigoProducto) {

        if (serial == null || serial.isBlank() || codigoProducto == null || codigoProducto.isBlank()) {
            return Resultado.rechazo(MotivoRechazo.DATOS_INVALIDOS);
        }

        String hash = hmacService.serialToHash(serial);
        Optional<Activador> opt = activadorRepository.findBySerialHash(hash);

        if (opt.isEmpty()) {
            return Resultado.rechazo(MotivoRechazo.SERIAL_NO_EXISTE);
        }

        Activador activador = opt.get();
        Licencia licencia = activador.getLicencia();

        if (licencia == null || !licencia.getCodigoProducto().equalsIgnoreCase(codigoProducto.trim())) {
            return Resultado.rechazo(MotivoRechazo.PRODUCTO_NO_COINCIDE);
        }
        if (licencia.getEstado() != EstadoLicencia.ACTIVA) {
            return Resultado.rechazo(MotivoRechazo.LICENCIA_DESACTIVA);
        }
        if (activador.getEstado() == EstadoActivador.REVOCADO) {
            return Resultado.rechazo(MotivoRechazo.SERIAL_REVOCADO);
        }

        return Resultado.ok();
    }

    /**
     * Validacion del instalador con identidad de equipo. Ademas de validar serial y producto,
     * compara la huella de hardware recibida con la registrada en {@code pc_info} y, si el
     * equipo esta autorizado, registra el uso del serial:
     * <ul>
     *   <li>sin {@code pc_info} (serial SIN_USO) -> registra la PC y pasa el activador a
     *       {@code OCUPADO};</li>
     *   <li>huella igual a la registrada -> permite (misma PC) y refresca las marcas de uso;</li>
     *   <li>huella distinta -> rechaza (otra PC).</li>
     * </ul>
     * Esta variante SI escribe: crea/actualiza {@code pc_info} y cambia el estado del serial.
     */
    @Transactional
    public Resultado validarConHardware(InstallerHardwareRequest req) {

        if (req == null || req.serial() == null || req.serial().isBlank()
                || req.codigoProducto() == null || req.codigoProducto().isBlank()) {
            return Resultado.rechazo(MotivoRechazo.DATOS_INVALIDOS);
        }

        String huella = identity.resolverHuella(req.versionHuella(), req.componentesHardware(), req.huellaPcHash());
        if (huella == null) {
            return Resultado.rechazo(MotivoRechazo.DATOS_INVALIDOS);
        }

        Resultado base = validar(req.serial(), req.codigoProducto());
        if (!base.permitido()) {
            return base;
        }

        String hash = hmacService.serialToHash(req.serial());
        Activador activador = activadorRepository.findBySerialHash(hash).orElseThrow();
        PcInfo pc = pcInfoRepository.findByActivadorId(activador.getId()).orElse(null);

        if (pc != null && !pc.getHuellaPcHash().equalsIgnoreCase(huella)) {
            return Resultado.rechazo(MotivoRechazo.EQUIPO_NO_AUTORIZADO);
        }

        LocalDateTime ahora = LocalDateTime.now();

        if (pc == null) {
            // Primer uso (SIN_USO) o estado inconsistente: se registra la computadora.
            String instalacionId = req.instalacionId() != null && !req.instalacionId().isBlank()
                    ? PcIdentitySupport.recortar(req.instalacionId(), 64)
                    : "INSTALADOR";
            pc = identity.nuevaPcInfo(activador, instalacionId, req.nombreEquipo(), req.sistemaOperativo(),
                    req.versionHuella(), huella, ahora);
            pcInfoRepository.save(pc);
            if (activador.getEstado() == EstadoActivador.SIN_USO) {
                activador.setEstado(EstadoActivador.OCUPADO);
                activadorRepository.save(activador);
            }
        } else {
            // Misma PC: solo se refrescan las marcas de uso.
            pc.setUltimaVerificacion(ahora);
            pc.setUltimaModificacion(ahora);
            pcInfoRepository.save(pc);
        }

        return Resultado.ok();
    }

}
