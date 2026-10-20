package com.zaga.licenciamientoapp.service.serial;

/**
 * Genera seriales con suficiente entropia criptografica.
 */
public interface SerialGenerator {

    /**
     * Genera un serial nuevo en el formato configurado (grupos separados por guiones).
     * El serial devuelto se entrega al cliente una sola vez; en la base de datos solo
     * se persiste su {@link HmacService#serialToHash(String) hash}.
     */
    String generar();
}
