package com.zaga.licenciamientoapp.web;

import com.zaga.licenciamientoapp.service.LicenseAdminService;
import com.zaga.licenciamientoapp.web.dto.EstadoLicenciaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta publica (de solo lectura) del estado de una licencia por producto/cliente.
 * No expone seriales ni identidades de equipos.
 */
@RestController
@RequestMapping("/api/licencia")
public class LicenciaEstadoController {

    private final LicenseAdminService adminService;

    public LicenciaEstadoController(LicenseAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/estado")
    public EstadoLicenciaResponse estado(@RequestParam String codigoProducto,
                                         @RequestParam(required = false) String clienteRef) {
        return adminService.estadoPorProductoYCliente(codigoProducto, clienteRef);
    }
}
