package com.zaga.licenciamientoapp.service;

import com.zaga.licenciamientoapp.domain.Activador;
import com.zaga.licenciamientoapp.domain.EstadoActivador;
import com.zaga.licenciamientoapp.domain.EstadoLicencia;
import com.zaga.licenciamientoapp.domain.Licencia;
import com.zaga.licenciamientoapp.domain.PcInfo;
import com.zaga.licenciamientoapp.exception.LicensingException;
import com.zaga.licenciamientoapp.exception.MotivoRechazo;
import com.zaga.licenciamientoapp.repository.ActivadorRepository;
import com.zaga.licenciamientoapp.repository.PcInfoRepository;
import com.zaga.licenciamientoapp.service.audit.AuditService;
import com.zaga.licenciamientoapp.service.offline.OfflineTokenService;
import com.zaga.licenciamientoapp.service.serial.HmacService;
import com.zaga.licenciamientoapp.web.dto.ActivacionResponse;
import com.zaga.licenciamientoapp.web.dto.InstallerHardwareRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Activacion y verificacion de computadoras. Toda la logica es transaccional y
 * bloquea el activador mediante SELECT ... FOR UPDATE para impedir que dos equipos
 * reclamen el mismo serial de forma simultanea.
 */
@Service
public class ActivationService {

    private static final Logger log = LoggerFactory.getLogger(ActivationService.class);

    private final ActivadorRepository activadorRepository;
    private final PcInfoRepository pcInfoRepository;
    private final HmacService hmacService;
    private final PcIdentitySupport identity;
    private final OfflineTokenService offlineTokenService;
    private final AuditService audit;

    public ActivationService(ActivadorRepository activadorRepository,
                             PcInfoRepository pcInfoRepository,
                             HmacService hmacService,
                             PcIdentitySupport identity,
                             OfflineTokenService offlineTokenService,
                             AuditService audit) {
        this.activadorRepository = activadorRepository;
        this.pcInfoRepository = pcInfoRepository;
        this.hmacService = hmacService;
        this.identity = identity;
        this.offlineTokenService = offlineTokenService;
        this.audit = audit;
    }

    // ============================================================= ACTIVAR

    @Transactional
    public ActivacionResponse activar(InstallerHardwareRequest req) {

        validarEntrada(req);
        String huella = resolverHuella(req);
        String hash = hmacService.serialToHash(req.serial());
        String serialMask = hmacService.mask(req.serial());

        Activador activador = activadorRepository.findBySerialHashForUpdate(hash)
                .orElseThrow(() -> rechazo(MotivoRechazo.SERIAL_NO_EXISTE, "activar", serialMask));
        verificarLicencia(activador, req.codigoProducto());

        LocalDateTime ahora = LocalDateTime.now();

        return switch (activador.getEstado()) {
            case REVOCADO -> throw rechazo(MotivoRechazo.SERIAL_REVOCADO, "activar", serialMask);

            case SIN_USO -> {
                PcInfo pc = nuevaPcInfo(activador, req, huella, ahora);
                pcInfoRepository.save(pc);
                activador.setEstado(EstadoActivador.OCUPADO);
                activadorRepository.save(activador);
                audit.activacion("PRIMERA_ACTIVACION", req.codigoProducto(), serialMask);
                yield respuesta(activador, pc, req, serialMask);
            }

            case OCUPADO -> {
                PcInfo pc = pcInfoRepository.findByActivadorId(activador.getId()).orElse(null);
                if (pc == null) {
                    // Estado inconsistente: se reconstruye la asignacion.
                    pc = nuevaPcInfo(activador, req, huella, ahora);
                    pcInfoRepository.save(pc);
                    audit.activacion("RECONSTRUCCION_PC_INFO", req.codigoProducto(), serialMask);
                    yield respuesta(activador, pc, req, serialMask);
                }
                if (!pc.getInstalacionId().equals(req.instalacionId())) {
                    throw rechazo(MotivoRechazo.EQUIPO_NO_AUTORIZADO, "activar", serialMask);
                }
                if (!pc.getHuellaPcHash().equalsIgnoreCase(huella)) {
                    throw rechazo(MotivoRechazo.HUELLA_NO_COINCIDE, "activar", serialMask);
                }
                pc.setUltimaVerificacion(ahora);
                pc.setUltimaModificacion(ahora);
                pcInfoRepository.save(pc);
                audit.activacion("VERIFICACION", req.codigoProducto(), serialMask);
                yield respuesta(activador, pc, req, serialMask);
            }

            case TRASPASO -> {
                PcInfo pc = pcInfoRepository.findByActivadorId(activador.getId()).orElse(null);
                if (pc == null) {
                    pc = nuevaPcInfo(activador, req, huella, ahora);
                } else {
                    actualizarIdentidad(pc, req, huella, ahora);
                }
                pcInfoRepository.save(pc);
                activador.setEstado(EstadoActivador.OCUPADO);
                activadorRepository.save(activador);
                audit.activacion("TRASLADO_COMPLETADO", req.codigoProducto(), serialMask);
                yield respuesta(activador, pc, req, serialMask);
            }
        };
    }

    // ============================================================= VERIFICAR

    @Transactional
    public ActivacionResponse verificar(InstallerHardwareRequest req) {

        validarEntrada(req);

        String huella = resolverHuella(req);
        String hash = hmacService.serialToHash(req.serial());
        String serialMask = hmacService.mask(req.serial());

        Activador activador = activadorRepository.findBySerialHashForUpdate(hash)
                .orElseThrow(() -> rechazo(MotivoRechazo.SERIAL_NO_EXISTE, "verificar", serialMask));
        verificarLicencia(activador, req.codigoProducto());

        if (activador.getEstado() == EstadoActivador.REVOCADO) {
            throw rechazo(MotivoRechazo.SERIAL_REVOCADO, "verificar", serialMask);
        }
        if (activador.getEstado() == EstadoActivador.SIN_USO) {
            throw rechazo(MotivoRechazo.SERIAL_NO_ACTIVADO, "verificar", serialMask);
        }
        if (activador.getEstado() == EstadoActivador.TRASPASO) {
            throw rechazo(MotivoRechazo.TRASPASO_NO_AUTORIZADO, "verificar", serialMask);
        }

        PcInfo pc = pcInfoRepository.findByActivadorId(activador.getId()).orElse(null);

        if (pc == null) {
            throw rechazo(MotivoRechazo.SERIAL_NO_ACTIVADO, "verificar", serialMask);
        }
        if (!pc.getInstalacionId().equals(req.instalacionId())) {
            throw rechazo(MotivoRechazo.EQUIPO_NO_AUTORIZADO, "verificar", serialMask);
        }
        if (!pc.getHuellaPcHash().equalsIgnoreCase(huella)) {
            throw rechazo(MotivoRechazo.HUELLA_NO_COINCIDE, "verificar", serialMask);
        }

        LocalDateTime ahora = LocalDateTime.now();

        pc.setUltimaVerificacion(ahora);
        pcInfoRepository.save(pc);
        audit.activacion("VERIFICACION_ONLINE", req.codigoProducto(), serialMask);

        return respuesta(activador, pc, req, serialMask);

    }

    // ============================================================= helpers

    private void validarEntrada(InstallerHardwareRequest req) {

        if (req == null) {
            throw new LicensingException(MotivoRechazo.DATOS_INVALIDOS, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (req.serial() == null || req.serial().isBlank()) {
            throw new LicensingException(MotivoRechazo.DATOS_INVALIDOS, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (req.codigoProducto() == null || req.codigoProducto().isBlank()) {
            throw new LicensingException(MotivoRechazo.DATOS_INVALIDOS, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (req.instalacionId() == null || req.instalacionId().isBlank() || req.instalacionId().length() > 64) {
            throw new LicensingException(MotivoRechazo.DATOS_INVALIDOS, org.springframework.http.HttpStatus.BAD_REQUEST);
        }

    }

    private void verificarLicencia(Activador activador, String codigoProducto) {
        Licencia licencia = activador.getLicencia();
        if (licencia == null || !licencia.getCodigoProducto().equalsIgnoreCase(codigoProducto.trim())) {
            throw new LicensingException(MotivoRechazo.PRODUCTO_NO_COINCIDE);
        }
        if (licencia.getEstado() != EstadoLicencia.ACTIVA) {
            throw new LicensingException(MotivoRechazo.LICENCIA_DESACTIVA);
        }
    }

    private String resolverHuella(InstallerHardwareRequest req) {
        String huella = identity.resolverHuella(req.versionHuella(), req.componentesHardware(), req.huellaPcHash());
        if (huella == null) {
            throw new LicensingException(MotivoRechazo.DATOS_INVALIDOS, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        return huella;
    }

    private PcInfo nuevaPcInfo(Activador activador, InstallerHardwareRequest req, String huella, LocalDateTime ahora) {
        return identity.nuevaPcInfo(activador, req.instalacionId(), req.nombreEquipo(), req.sistemaOperativo(),
                req.versionHuella(), huella, ahora);
    }

    private void actualizarIdentidad(PcInfo pc, InstallerHardwareRequest req, String huella, LocalDateTime ahora) {
        pc.setInstalacionId(req.instalacionId());
        pc.setHuellaPcHash(huella);
        pc.setNombreEquipo(PcIdentitySupport.recortar(req.nombreEquipo(), 128));
        pc.setSistemaOperativo(PcIdentitySupport.recortar(req.sistemaOperativo(), 64));
        pc.setVersionHuella(req.versionHuella() != null ? req.versionHuella() : identity.versionActual());
        pc.setUltimaVerificacion(ahora);
        pc.setUltimaModificacion(ahora);
    }

    private ActivacionResponse respuesta(Activador activador, PcInfo pc, InstallerHardwareRequest req, String serialMask) {
        String token = offlineTokenService.emitir(
                req.codigoProducto(), pc.getInstalacionId(), pc.getHuellaPcHash(),
                serialMask, LocalDateTime.now());
        return new ActivacionResponse(
                true,
                "ACTIVACION_OK",
                "Activacion correcta.",
                activador.getEstado().name(),
                pc.getInstalacionId(),
                pc.getHuellaPcHash(),
                String.valueOf(pc.getPrimeraActivacion()),
                String.valueOf(pc.getUltimaVerificacion()),
                token);
    }

    private LicensingException rechazo(MotivoRechazo motivo, String contexto, String serialMask) {
        log.info("Rechazo de {}: {} serial={}", contexto, motivo.getCodigo(), serialMask);
        return new LicensingException(motivo);
    }
}
