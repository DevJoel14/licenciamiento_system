package com.zaga.licenciamientoapp.service;

import com.zaga.licenciamientoapp.domain.Activador;
import com.zaga.licenciamientoapp.domain.EstadoActivador;
import com.zaga.licenciamientoapp.domain.EstadoLicencia;
import com.zaga.licenciamientoapp.domain.Licencia;
import com.zaga.licenciamientoapp.domain.PcInfo;
import com.zaga.licenciamientoapp.exception.LicensingException;
import com.zaga.licenciamientoapp.exception.MotivoRechazo;
import com.zaga.licenciamientoapp.exception.ResourceNotFoundException;
import com.zaga.licenciamientoapp.repository.ActivadorRepository;
import com.zaga.licenciamientoapp.repository.LicenciaRepository;
import com.zaga.licenciamientoapp.repository.PcInfoRepository;
import com.zaga.licenciamientoapp.service.audit.AuditService;
import com.zaga.licenciamientoapp.service.serial.HmacService;
import com.zaga.licenciamientoapp.service.serial.SerialGenerator;
import com.zaga.licenciamientoapp.web.dto.ActivadorResponse;
import com.zaga.licenciamientoapp.web.dto.EstadoLicenciaResponse;
import com.zaga.licenciamientoapp.web.dto.LicenciaResponse;
import com.zaga.licenciamientoapp.web.dto.LicenciaResumenResponse;
import com.zaga.licenciamientoapp.web.dto.SerialResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operaciones administrativas: alta de licencias y seriales, desactivacion, revocacion
 * y autorizacion de traslados. La logica de negocio reside aqui, no en el instalador
 * ni en el cliente.
 */
@Service
public class LicenseAdminService {

    private final LicenciaRepository licenciaRepository;
    private final ActivadorRepository activadorRepository;
    private final PcInfoRepository pcInfoRepository;
    private final SerialGenerator serialGenerator;
    private final HmacService hmacService;
    private final AuditService audit;

    public LicenseAdminService(LicenciaRepository licenciaRepository,
                               ActivadorRepository activadorRepository,
                               PcInfoRepository pcInfoRepository,
                               SerialGenerator serialGenerator,
                               HmacService hmacService,
                               AuditService audit) {
        this.licenciaRepository = licenciaRepository;
        this.activadorRepository = activadorRepository;
        this.pcInfoRepository = pcInfoRepository;
        this.serialGenerator = serialGenerator;
        this.hmacService = hmacService;
        this.audit = audit;
    }

    // ------------------------------------------------------------- licencias

    @Transactional
    public LicenciaResponse crearLicencia(String codigoProducto, String clienteRef, int cantidadSeriales) {
        Licencia licencia = new Licencia(codigoProducto.trim(), clienteRef.trim());
        licencia = licenciaRepository.save(licencia);

        List<SerialResponse> generados = new ArrayList<>();
        if (cantidadSeriales > 0) {
            generados = generarSeriales(licencia, cantidadSeriales);
        }
        audit.licenciaCreada(licencia.getId(), licencia.getCodigoProducto(), licencia.getClienteRef(), generados.size());
        return mapLicencia(licencia, generados);
    }

    @Transactional
    public List<SerialResponse> agregarSeriales(Long licenciaId, int cantidad) {
        Licencia licencia = licenciaRepository.findById(licenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Licencia no encontrada: " + licenciaId));
        if (licencia.getEstado() == EstadoLicencia.DESACTIVA) {
            throw new LicensingException(MotivoRechazo.LICENCIA_DESACTIVA);
        }
        List<SerialResponse> generados = generarSeriales(licencia, cantidad);
        audit.serialesAgregados(licenciaId, generados.size());
        return generados;
    }

    private List<SerialResponse> generarSeriales(Licencia licencia, int cantidad) {
        List<SerialResponse> generados = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) {
            String serial;
            String hash;
            int intentos = 0;
            do {
                serial = serialGenerator.generar();
                hash = hmacService.serialToHash(serial);
                intentos++;
            } while (activadorRepository.findBySerialHash(hash).isPresent() && intentos < 10);
            if (activadorRepository.findBySerialHash(hash).isPresent()) {
                throw new IllegalStateException("No fue posible generar un serial unico tras varios intentos.");
            }
            Activador activador = activadorRepository.save(new Activador(licencia, hash));
            generados.add(new SerialResponse(
                    activador.getId(), serial, activador.getEstado().name(),
                    String.valueOf(activador.getFechaCreacion())));
        }
        return generados;
    }

    @Transactional
    public LicenciaResponse cambiarEstadoLicencia(Long licenciaId, EstadoLicencia nuevoEstado) {
        Licencia licencia = licenciaRepository.findById(licenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Licencia no encontrada: " + licenciaId));
        licencia.setEstado(nuevoEstado);
        licenciaRepository.save(licencia);
        audit.licenciaEstado(licenciaId, nuevoEstado.name());
        return mapLicencia(licencia, null);
    }

    @Transactional(readOnly = true)
    public LicenciaResponse obtenerLicencia(Long licenciaId) {

        Licencia licencia = licenciaRepository.findById(licenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Licencia no encontrada: " + licenciaId));

        List<SerialResponse> seriales = activadorRepository.findByLicenciaId(licenciaId).stream()
                .map(a -> new SerialResponse(a.getId(), null, a.getEstado().name(),
                        String.valueOf(a.getFechaCreacion())))
                .toList();

        return mapLicencia(licencia, seriales);

    }

    // ------------------------------------------------------------- activadores

    @Transactional
    public ActivadorResponse revocarActivador(Long activadorId) {
        Activador activador = activadorRepository.findById(activadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Activador no encontrado: " + activadorId));
        activador.setEstado(EstadoActivador.REVOCADO);
        activadorRepository.save(activador);
        audit.serialRevocado(activadorId);
        return mapActivador(activador);
    }

    /**
     * Autoriza el traslado de un serial: pasa su estado a TRASPASO. El equipo nuevo
     * completara la activacion, momento en el que se actualiza PC_INFO.
     */
    @Transactional
    public ActivadorResponse autorizarTraspaso(Long activadorId, String autorizadoPor) {
        Activador activador = activadorRepository.findByIdForUpdate(activadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Activador no encontrado: " + activadorId));

        if (activador.getEstado() == EstadoActivador.REVOCADO) {
            throw new LicensingException(MotivoRechazo.SERIAL_REVOCADO);
        }
        if (activador.getEstado() == EstadoActivador.TRASPASO) {
            // idempotente: ya esta autorizado
            return mapActivador(activador);
        }
        if (activador.getEstado() != EstadoActivador.OCUPADO) {
            throw new LicensingException(MotivoRechazo.TRASPASO_NO_AUTORIZADO);
        }
        activador.setEstado(EstadoActivador.TRASPASO);
        activadorRepository.save(activador);
        audit.traspasoAutorizado(activadorId, autorizadoPor);
        return mapActivador(activador);
    }

    @Transactional(readOnly = true)
    public List<ActivadorResponse> listarActivadores(Long licenciaId) {
        List<Activador> base = (licenciaId == null)
                ? activadorRepository.findAll()
                : activadorRepository.findByLicenciaId(licenciaId);
        return base.stream().map(this::mapActivador).toList();
    }

    @Transactional(readOnly = true)
    public ActivadorResponse obtenerActivador(Long activadorId) {
        Activador activador = activadorRepository.findById(activadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Activador no encontrado: " + activadorId));
        return mapActivador(activador);
    }

    // ------------------------------------------------------------- consultas

    /**
     * Busqueda administrativa por producto y/o cliente. Ambos filtros son opcionales:
     * sin filtros devuelve todas las licencias. Ignora mayusculas y admite coincidencias
     * parciales (p. ej. buscar solo el cliente).
     */
    @Transactional(readOnly = true)
    public List<LicenciaResumenResponse> buscarLicencias(String codigoProducto, String clienteRef) {
        String producto = normalizarFiltro(codigoProducto, true);
        String cliente = normalizarFiltro(clienteRef, false);
        return licenciaRepository.buscar(producto, cliente).stream()
                .map(l -> new LicenciaResumenResponse(
                        l.getId(),
                        l.getCodigoProducto(),
                        l.getClienteRef(),
                        l.getEstado().name(),
                        String.valueOf(l.getFechaCreacion()),
                        activadorRepository.countByLicenciaId(l.getId())))
                .toList();
    }

    private static String normalizarFiltro(String valor, boolean producto) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim();
        return producto ? limpio.replace('_', ' ') : limpio;
    }

    @Transactional(readOnly = true)
    public EstadoLicenciaResponse estadoPorProductoYCliente(String codigoProducto, String clienteRef) {
        List<Licencia> licencias = (clienteRef == null || clienteRef.isBlank())
                ? licenciaRepository.findByCodigoProducto(codigoProducto)
                : licenciaRepository.findByCodigoProductoAndClienteRef(codigoProducto, clienteRef);
        if (licencias.isEmpty()) {
            throw new ResourceNotFoundException("No se encontro licencia para el producto/cliente indicado.");
        }
        Licencia licencia = licencias.get(0);
        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (EstadoActivador e : EstadoActivador.values()) {
            porEstado.put(e.name(), activadorRepository.countByLicenciaIdAndEstado(licencia.getId(), e));
        }
        long total = porEstado.values().stream().mapToLong(Long::longValue).sum();
        return new EstadoLicenciaResponse(true, licencia.getId(), licencia.getCodigoProducto(),
                licencia.getClienteRef(), licencia.getEstado().name(), total, porEstado);
    }

    // ------------------------------------------------------------- mapeos

    private LicenciaResponse mapLicencia(Licencia licencia, List<SerialResponse> seriales) {
        return new LicenciaResponse(
                licencia.getId(),
                licencia.getCodigoProducto(),
                licencia.getClienteRef(),
                licencia.getEstado().name(),
                String.valueOf(licencia.getFechaCreacion()),
                seriales);
    }

    private ActivadorResponse mapActivador(Activador activador) {
        PcInfo pc = pcInfoRepository.findByActivadorId(activador.getId()).orElse(null);
        return new ActivadorResponse(
                activador.getId(),
                activador.getLicencia() != null ? activador.getLicencia().getId() : null,
                activador.getEstado().name(),
                String.valueOf(activador.getFechaCreacion()),
                pc != null ? pc.getInstalacionId() : null,
                pc != null ? pc.getHuellaPcHash() : null,
                pc != null ? pc.getNombreEquipo() : null,
                pc != null ? pc.getSistemaOperativo() : null,
                pc != null ? pc.getVersionHuella() : null,
                pc != null && pc.getPrimeraActivacion() != null ? String.valueOf(pc.getPrimeraActivacion()) : null,
                pc != null && pc.getUltimaVerificacion() != null ? String.valueOf(pc.getUltimaVerificacion()) : null);
    }
}
