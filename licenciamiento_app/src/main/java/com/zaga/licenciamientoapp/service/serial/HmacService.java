package com.zaga.licenciamientoapp.service.serial;

/**
 * Convierte un serial en su hash HMAC-SHA-256 para indexarlo en la base de datos
 * sin almacenarlo en texto plano. El secreto permanece exclusivamente en el servidor.
 */
public interface HmacService {

    /**
     * Normaliza el serial (mayusculas, sin separadores) y calcula su HMAC-SHA-256 en hex.
     *
     * @param serial serial introducido por el usuario (puede contener guiones/espacios)
     * @return hash hexadecimal en minusculas de 64 caracteres
     */
    String serialToHash(String serial);

    /** Normaliza un serial al formato canonico (mayusculas, sin separadores). */
    String normalize(String serial);

    /** Devuelve una representacion enmascarada segura para logs (no expone el serial completo). */
    default String mask(String serial) {
        if (serial == null || serial.isBlank()) {
            return "****";
        }
        String n = normalize(serial);
        if (n.length() <= 4) {
            return "****";
        }
        return "****" + n.substring(n.length() - 4);
    }
}
