package com.zaga.licenciamientoapp.exception;

/**
 * Motivos de rechazo de negocio. Cada uno tiene un codigo estable y un mensaje
 * por defecto pensado para no revelar informacion sensible al cliente.
 */
public enum MotivoRechazo {

    DATOS_INVALIDOS("DATOS_INVALIDOS", "La solicitud contiene datos invalidos o incompletos."),
    SERIAL_NO_EXISTE("SERIAL_NO_EXISTE", "El serial no existe."),
    PRODUCTO_NO_COINCIDE("PRODUCTO_NO_COINCIDE", "El serial no corresponde al producto solicitado."),
    LICENCIA_DESACTIVA("LICENCIA_DESACTIVA", "La licencia esta desactivada."),
    SERIAL_REVOCADO("SERIAL_REVOCADO", "El serial fue revocado."),
    EQUIPO_NO_AUTORIZADO("EQUIPO_NO_AUTORIZADO", "El serial ya esta asociado a otra computadora."),
    HUELLA_NO_COINCIDE("HUELLA_NO_COINCIDE", "La identidad del equipo no coincide con la registrada."),
    TRASPASO_NO_AUTORIZADO("TRASPASO_NO_AUTORIZADO", "El serial no esta autorizado para traslado."),
    SERIAL_NO_ACTIVADO("SERIAL_NO_ACTIVADO", "El serial aun no ha sido activado en ningun equipo."),
    SERIAL_YA_OCUPADO("SERIAL_YA_OCUPADO", "El serial ya fue activado en este equipo no autorizado.");

    private final String codigo;
    private final String mensaje;

    MotivoRechazo(String codigo, String mensaje) {
        this.codigo = codigo;
        this.mensaje = mensaje;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getMensaje() {
        return mensaje;
    }
}
