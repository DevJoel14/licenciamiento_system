package com.zaga.licenciamientoapp.service.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Registro de operaciones administrativas y errores relevantes.
 *
 * El modelo inicial no requiere una tabla de historial; los eventos se emiten a un
 * logger dedicado ({@code AUDIT}) que puede redirigirse a un sistema de auditoria.
 * NUNCA se registran secretos ni seriales completos.
 */
@Service
public class AuditService {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    public void adminAction(String operacion, String actor, String detalle) {
        audit.info("op={} actor={} detalle={}", operacion, actor, detalle);
    }

    public void licenciaCreada(Long id, String codigoProducto, String clienteRef, int seriales) {
        audit.info("op=CREAR_LICENCIA licenciaId={} producto={} cliente={} seriales={}",
                id, codigoProducto, clienteRef, seriales);
    }

    public void serialesAgregados(Long licenciaId, int cantidad) {
        audit.info("op=AGREGAR_SERIALES licenciaId={} cantidad={}", licenciaId, cantidad);
    }

    public void licenciaEstado(Long licenciaId, String nuevoEstado) {
        audit.info("op=CAMBIAR_ESTADO_LICENCIA licenciaId={} estado={}", licenciaId, nuevoEstado);
    }

    public void serialRevocado(Long activadorId) {
        audit.info("op=REVOCAR_SERIAL activadorId={}", activadorId);
    }

    public void traspasoAutorizado(Long activadorId, String autorizadoPor) {
        audit.info("op=AUTORIZAR_TRASPASO activadorId={} autorizadoPor={}", activadorId, autorizadoPor);
    }

    public void activacion(String resultado, String codigoProducto, String serialEnmascarado) {
        audit.info("op=ACTIVACION resultado={} producto={} serial={}",
                resultado, codigoProducto, serialEnmascarado);
    }
}
