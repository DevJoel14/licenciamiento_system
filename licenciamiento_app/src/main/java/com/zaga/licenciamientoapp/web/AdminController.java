package com.zaga.licenciamientoapp.web;

import com.zaga.licenciamientoapp.domain.EstadoLicencia;
import com.zaga.licenciamientoapp.service.LicenseAdminService;
import com.zaga.licenciamientoapp.web.dto.ActivadorResponse;
import com.zaga.licenciamientoapp.web.dto.AgregarSerialesRequest;
import com.zaga.licenciamientoapp.web.dto.CrearLicenciaRequest;
import com.zaga.licenciamientoapp.web.dto.LicenciaResponse;
import com.zaga.licenciamientoapp.web.dto.LicenciaResumenResponse;
import com.zaga.licenciamientoapp.web.dto.SerialResponse;
import com.zaga.licenciamientoapp.web.dto.TraspasoRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Operaciones administrativas. Todas requieren la cabecera {@code X-Admin-Api-Key}
 * (ver {@link com.zaga.licenciamientoapp.config.AdminAuthFilter}).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final LicenseAdminService adminService;

    public AdminController(LicenseAdminService adminService) {
        this.adminService = adminService;
    }

    // ------------------------------------------------------------- licencias

    /** Crea una licencia y, opcionalmente, N seriales iniciales. */
    @PostMapping("/licencias")
    public LicenciaResponse crearLicencia(@Valid @RequestBody CrearLicenciaRequest request) {
        return adminService.crearLicencia(
                request.codigoProducto(), request.clienteRef(), request.serialesSolicitados());
    }

    /**
     * Busca licencias por producto y/o cliente (ambos filtros opcionales).
     * Sin filtros devuelve todas las licencias.
     */
    @GetMapping("/licencias")
    public List<LicenciaResumenResponse> buscarLicencias(
            @RequestParam(required = false) String codigoProducto,
            @RequestParam(required = false) String clienteRef) {
        return adminService.buscarLicencias(codigoProducto, clienteRef);
    }

    @GetMapping("/licencias/{id}")
    public LicenciaResponse obtenerLicencia(@PathVariable Long id) {
        return adminService.obtenerLicencia(id);
    }

    /** Agrega puestos adicionales (seriales) a una licencia existente. */
    @PostMapping("/licencias/{id}/seriales")
    public List<SerialResponse> agregarSeriales(@PathVariable Long id,
                                                @Valid @RequestBody AgregarSerialesRequest request) {
        return adminService.agregarSeriales(id, request.cantidad());
    }

    @PostMapping("/licencias/{id}/desactivar")
    public LicenciaResponse desactivar(@PathVariable Long id) {
        return adminService.cambiarEstadoLicencia(id, EstadoLicencia.DESACTIVA);
    }

    @PostMapping("/licencias/{id}/activar")
    public LicenciaResponse activar(@PathVariable Long id) {
        return adminService.cambiarEstadoLicencia(id, EstadoLicencia.ACTIVA);
    }

    // ------------------------------------------------------------- activadores

    @GetMapping("/activadores")
    public List<ActivadorResponse> listarActivadores(@RequestParam(required = false) Long licenciaId) {
        return adminService.listarActivadores(licenciaId);
    }

    @GetMapping("/activadores/{id}")
    public ActivadorResponse obtenerActivador(@PathVariable Long id) {
        return adminService.obtenerActivador(id);
    }

    /** Revoca un serial de forma permanente. */
    @PostMapping("/activadores/{id}/revocar")
    public ActivadorResponse revocar(@PathVariable Long id) {
        return adminService.revocarActivador(id);
    }

    /**
     * Autoriza un traslado: pasa el serial a TRASPASO. El equipo nuevo debera activar
     * para completar el cambio; mientras tanto el serial queda en TRASPASO.
     */
    @PostMapping("/activadores/{id}/traspaso")
    public ActivadorResponse autorizarTraspaso(@PathVariable Long id,
                                               @Valid @RequestBody TraspasoRequest request) {
        return adminService.autorizarTraspaso(id, request.autorizadoPor());
    }
}
