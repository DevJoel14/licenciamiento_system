-- =============================================================================
-- Sistema de licenciamiento - DDL para MySQL 8
-- Tres tablas principales: LICENCIAS, ACTIVADORES, PC_INFO
-- Ejecutar una sola vez sobre una base de datos vacia.
-- =============================================================================

-- CREATE DATABASE licenciamiento CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- CREATE USER 'licenciamiento'@'%' IDENTIFIED BY 'cambiar_esto';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON licenciamiento.* TO 'licenciamiento'@'%';
-- FLUSH PRIVILEGES;

-- -----------------------------------------------------------------------------
-- LICENCIAS: licencia comercial de un producto
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS licencias (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    codigo_producto  VARCHAR(64)  NOT NULL,
    cliente_ref      VARCHAR(128) NOT NULL,
    estado           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVA',
    fecha_creacion   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_licencias_producto (codigo_producto),
    KEY idx_licencias_cliente (cliente_ref),
    CONSTRAINT chk_licencias_estado CHECK (estado IN ('ACTIVA', 'DESACTIVA'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- ACTIVADORES: un serial individual. serial_hash = HMAC-SHA-256 del serial.
-- El serial original NUNCA se almacena en claro.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS activadores (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    licencia_id    BIGINT      NOT NULL,
    serial_hash    CHAR(64)    NOT NULL,
    estado         VARCHAR(16) NOT NULL DEFAULT 'SIN_USO',
    fecha_creacion DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_activadores_serial_hash (serial_hash),
    KEY idx_activadores_licencia (licencia_id),
    CONSTRAINT fk_activadores_licencia FOREIGN KEY (licencia_id)
        REFERENCES licencias (id) ON DELETE CASCADE,
    CONSTRAINT chk_activadores_estado CHECK (estado IN ('SIN_USO', 'OCUPADO', 'TRASPASO', 'REVOCADO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- PC_INFO: asignacion actual de un serial a una computadora (1:1 con activador).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pc_info (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    activador_id        BIGINT       NOT NULL,
    instalacion_id      VARCHAR(64)  NOT NULL,
    huella_pc_hash      CHAR(64)     NOT NULL,
    nombre_equipo       VARCHAR(128) NULL,
    sistema_operativo   VARCHAR(64)  NULL,
    version_huella      INT          NOT NULL DEFAULT 1,
    primera_activacion  DATETIME(6)  NOT NULL,
    ultima_verificacion DATETIME(6)  NULL,
    ultima_modificacion DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pcinfo_activador (activador_id),
    CONSTRAINT fk_pcinfo_activador FOREIGN KEY (activador_id)
        REFERENCES activadores (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
