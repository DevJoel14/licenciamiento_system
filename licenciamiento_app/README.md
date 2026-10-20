["#ccd5ae","#e9edc9","#fefae0","#faedcd","#d4a373"]
["#ede0d4","#e6ccb2","#ddb892","#b08968","#7f5539","#9c6644"]
///// docker ///////
docker-compose down
docker-compose build
docker-compose up -d

# Sistema de Licenciamiento (Spring Boot)

Backend centralizado y **agnóstico** para comercializar software de escritorio mediante
licencias por producto y activaciones por computadora. Toda la interacción ocurre por
**HTTP/HTTPS + JSON**: el cliente puede estar en Java, C#, C++, Kotlin o cualquier
tecnología capaz de hacer HTTP. El backend no depende de ningún framework de interfaz
gráfica.

## Arquitectura

| Componente | Rol |
|---|---|
| Spring Boot | Autoridad central: valida productos, licencias, seriales, estados y equipos |
| MySQL 8 | Base de datos central (3 tablas) |
| H2 (perfil `dev` / tests) | Base local en memoria para desarrollo y pruebas |
| Advanced Installer | Únicamente validación previa del serial (no toca la base) |
| App de escritorio | Activación y verificación en línea por HTTPS |

## Modelo de datos (3 tablas)

- **LICENCIAS**: licencia comercial de un producto (`codigo_producto`, `cliente_ref`, `estado`, `fecha_creacion`).
- **ACTIVADORES**: un serial individual (`licencia_id`, `serial_hash` HMAC-SHA-256 UNIQUE, `estado`, `fecha_creacion`).
  Estados: `SIN_USO`, `OCUPADO`, `TRASPASO`, `REVOCADO`.
- **PC_INFO**: asignación actual de un serial a una computadora (`activador_id` UNIQUE, `instalacion_id`,
  `huella_pc_hash`, `nombre_equipo`, `sistema_operativo`, `version_huella`, `primera_activacion`,
  `ultima_verificacion`, `ultima_modificacion`).

DDL: [`src/main/resources/db/schema-mysql.sql`](src/main/resources/db/schema-mysql.sql).

El serial original **nunca** se guarda: solo su HMAC-SHA-256. El secreto HMAC permanece en el servidor.

---

## Funcionalidades (qué recibe y qué devuelve)

Convenciones:
- Base URL de ejemplo: `http://localhost:8085`.
- Administración requiere la cabecera `X-Admin-Api-Key` (por defecto `dev-admin-key-change-me`).
- Códigos de rechazo de negocio → **HTTP 409**; datos inválidos → **400**; errores internos → **500**.

### 1. Validar serial en el instalador (Advanced Installer)

- **Recibe:** `sn` (serial) y `ai` (código de producto) — los nombres que usa Advanced Installer en validación server-side. También acepta `serial`/`producto` y query string.
- **Devuelve:** texto plano `601` (permitir) o `602\n<motivo>` (rechazar). HTTP 200.
- **Regla:** debe existir el serial, corresponder al producto, la licencia estar `ACTIVA` y el serial no estar `REVOCADO`.
- **Efecto:** ninguno (solo lectura e idempotente; no registra PC ni consume puestos).

```
POST /api/instalador/validar
Content-Type: application/x-www-form-urlencoded

serial=ABCDE-FGHIJ-KLMNO-PQRST-UVWXY&producto=SISTEMA_TALLER
```
```
601
```

Configuración en Advanced Installer: ver [`docs/DESPLIEGUE.md`](docs/DESPLIEGUE.md) (sección 6).

### 1.b Validar serial + identidad de equipo (custom action)

- **Recibe (JSON):** `serial`, `codigoProducto`, `instalacionId` y **uno** de `componentesHardware` (lista) o `huellaPcHash` (64 hex).
- **Devuelve (JSON):** `{ "permitido": true|false, "codigo": "601"|"602", "motivo": ..., "mensaje": ... }`.
- **Regla:** además de serial/producto/licencia, si el serial **ya está ocupado** se compara la huella con la registrada: **misma PC → permitido**, **otra PC → `EQUIPO_NO_AUTORIZADO`**. Si está `SIN_USO` (sin PC registrada) → permitido.
- **Efecto:** si el serial está `SIN_USO`, **registra la PC** (crea `PC_INFO` con la huella) y pasa el activador a `OCUPADO`; si ya estaba `OCUPADO` con la misma huella, refresca las marcas de uso. No emite token (eso ocurre en `/api/activacion/activar`).

```
POST /api/instalador/validar-hardware
Content-Type: application/json

{ "serial": "ABCDE-FGHIJ-KLMNO-PQRST-UVWXY", "codigoProducto": "SISTEMA_TALLER",
  "instalacionId": "PC-CLIENTE", "versionHuella": 1,
  "componentesHardware": ["cpu:...", "board:...", "mac:..."] }
```
```
{ "permitido": true, "codigo": "601", "motivo": null, "mensaje": "Equipo autorizado." }
```

Se invoca desde un **custom action (PowerShell)** de Advanced Installer que envía la huella.

### 2. Activar una computadora

- **Recibe:** `serial`, `codigoProducto`, `instalacionId`, y huella (`componentesHardware[]` + `versionHuella`,
  o bien `huellaPcHash`); opcional `nombreEquipo`, `sistemaOperativo`.
- **Devuelve (200):** `exitoso`, `codigo`, `mensaje`, `estadoActivador`, `instalacionId`, `huellaPcHash`,
  `primeraActivacion`, `ultimaVerificacion`, `tokenOffline`.
- **Devuelve (409):** `{exitoso:false, tipo:"NEGOCIO", codigo, mensaje}` con un motivo.
- **Efecto transaccional (`SELECT ... FOR UPDATE`):**

| Estado del serial | Acción |
|---|---|
| `SIN_USO` | Registra `PC_INFO` y pasa a `OCUPADO` |
| `OCUPADO` + mismo equipo | Verificación (actualiza `ultimaVerificacion`), sin duplicar |
| `OCUPADO` + otro equipo | Rechaza `EQUIPO_NO_AUTORIZADO` |
| `TRASPASO` | Actualiza `PC_INFO` con la nueva identidad → `OCUPADO` |
| `REVOCADO` / licencia `DESACTIVA` | Rechaza |

```
POST /api/activacion/activar
Content-Type: application/json

{
  "serial": "ABCDE-FGHIJ-KLMNO-PQRST-UVWXY",
  "codigoProducto": "SISTEMA_TALLER",
  "instalacionId": "3f8c1c2e-1a2b-4c3d-9e8f-0123456789ab",
  "versionHuella": 1,
  "nombreEquipo": "PC-CONTABILIDAD",
  "sistemaOperativo": "Windows 11 Pro",
  "componentesHardware": ["cpu:BFEBFBFF000806E9", "board:HP-85A2", "mac:001122334455"]
}
```

```json
{ "exitoso": true, "codigo": "ACTIVACION_OK", "mensaje": "Activacion correcta.",
  "estadoActivador": "OCUPADO", "instalacionId": "3f8c1c2e-...", "huellaPcHash": "9f2c...(64 hex)",
  "primeraActivacion": "2026-10-15T14:51:46.72", "ultimaVerificacion": "2026-10-15T14:51:46.72",
  "tokenOffline": "eyJ... . MEUCIQ..." }
```

### 3. Verificar una activación existente

- **Recibe:** igual que activar.
- **Devuelve:** `exitoso:true` si el serial está `OCUPADO` y coinciden `instalacionId` + huella; si no, `409` con el motivo.
- **Efecto:** actualiza `ultimaVerificacion`; **no** crea asignaciones nuevas.

```
POST /api/activacion/verificar
{ "serial": "...", "codigoProducto": "SISTEMA_TALLER", "instalacionId": "inst-A",
  "versionHuella": 1, "componentesHardware": ["cpu:AAA111", "board:BBB222"] }
```

### 4. Consultar estado de una licencia

- **Recibe:** `codigoProducto` (obligatorio) y `clienteRef` (opcional).
- **Devuelve:** `id`, `codigoProducto`, `clienteRef`, `estado`, `totalSeriales` y `serialesPorEstado` (conteo por estado).

```
GET /api/licencia/estado?codigoProducto=SISTEMA_TALLER&clienteRef=ACME
```

```json
{ "exitoso": true, "licenciaId": 1, "codigoProducto": "SISTEMA_TALLER", "clienteRef": "ACME",
  "estado": "ACTIVA", "totalSeriales": 5,
  "serialesPorEstado": { "SIN_USO": 3, "OCUPADO": 2, "TRASPASO": 0, "REVOCADO": 0 } }
```

### 5. Clave pública para el token offline

- **Recibe:** nada.
- **Devuelve:** `{ "publicKey": "-----BEGIN PUBLIC KEY-----..." }` (para verificar la firma del `tokenOffline`).

```
GET /api/public-key
```

### 6. Administración — licencias

| Caso de uso | Request | Devuelve |
|---|---|---|
| Crear licencia | `POST /api/admin/licencias` `{codigoProducto, clienteRef, cantidadSeriales}` | Licencia + `seriales[]` **con serial en claro (una sola vez)** |
| Buscar | `GET /api/admin/licencias?codigoProducto=&clienteRef=` | Lista de licencias (filtros opcionales, sin distinguir mayúsculas; `clienteRef` opcional) |
| Detalle | `GET /api/admin/licencias/{id}` | Licencia + seriales (sin valor) |
| Agregar puestos | `POST /api/admin/licencias/{id}/seriales` `{cantidad}` | `seriales[]` nuevos (en claro, una vez) |
| Desactivar | `POST /api/admin/licencias/{id}/desactivar` | Licencia `estado:"DESACTIVA"` |
| Reactivar | `POST /api/admin/licencias/{id}/activar` | Licencia `estado:"ACTIVA"` |

```json
POST /api/admin/licencias
{ "codigoProducto": "SISTEMA_TALLER", "clienteRef": "ACME", "cantidadSeriales": 2 }
```
```json
{ "id": 1, "codigoProducto": "SISTEMA_TALLER", "clienteRef": "ACME", "estado": "ACTIVA",
  "fechaCreacion": "2026-10-15T14:51:46.72",
  "seriales": [ { "activadorId": 1, "serial": "ABCDE-FGHIJ-KLMNO-PQRST-UVWXY", "estado": "SIN_USO", "fechaCreacion": "..." },
                { "activadorId": 2, "serial": "ZYXWV-UTSRQ-PONML-KJIHG-FEDCB", "estado": "SIN_USO", "fechaCreacion": "..." } ] }
```

### 7. Administración — activadores

| Caso de uso | Request | Devuelve |
|---|---|---|
| Listar | `GET /api/admin/activadores?licenciaId=` | Lista de activadores + equipo asignado |
| Detalle | `GET /api/admin/activadores/{id}` | Un activador + datos de PC |
| Revocar | `POST /api/admin/activadores/{id}/revocar` | Activador `estado:"REVOCADO"` |
| Autorizar traslado | `POST /api/admin/activadores/{id}/traspaso` `{autorizadoPor}` | Activador `estado:"TRASPASO"` |

```json
POST /api/admin/activadores/1/traslado
{ "autorizadoPor": "soporte@acme" }
```
```json
{ "id": 1, "licenciaId": 1, "estado": "TRASPASO", "fechaCreacion": "...",
  "instalacionId": "inst-A", "huellaPcHash": "9f2c...", "nombreEquipo": "PC-A",
  "sistemaOperativo": "Windows 11", "versionHuella": 1,
  "primeraActivacion": "...", "ultimaVerificacion": "..." }
```

### 8. Panel web

- `GET /admin` — interfaz que consume la API con la `X-Admin-Api-Key`: crear licencia, **generar y copiar seriales**, agregar puestos, desactivar/reactivar, listar activadores, traslado y revocación.

### Códigos de respuesta

| Tipo | HTTP | Ejemplo |
|---|---|---|
| Éxito | 200 | `{ "exitoso": true, ... }` |
| Rechazo de negocio | 409 | `EQUIPO_NO_AUTORIZADO`, `SERIAL_REVOCADO`, `HUELLA_NO_COINCIDE`, ... |
| Datos inválidos | 400 | `DATOS_INVALIDOS` |
| No autorizado (admin) | 401 | `NO_AUTORIZADO` |
| No encontrado | 404 | `NO_ENCONTRADO` |
| Demasiadas peticiones | 429 | `DEMASIADAS_PETICIONES` |
| Error interno | 500 | `ERROR_INTERNO` |

Motivos de rechazo: `SERIAL_NO_EXISTE`, `PRODUCTO_NO_COINCIDE`, `LICENCIA_DESACTIVA`, `SERIAL_REVOCADO`,
`EQUIPO_NO_AUTORIZADO`, `HUELLA_NO_COINCIDE`, `TRASPASO_NO_AUTORIZADO`, `SERIAL_NO_ACTIVADO`, `DATOS_INVALIDOS`.

---

## Flujos

**Venta e instalación:** administrador crea licencia (#6) → entrega seriales → instalador valida (#1 = `601`)
→ app activa (#2, `SIN_USO`→`OCUPADO`) → verificaciones periódicas (#3).

**Cliente cambia de computadora:** el cliente solicita el cambio → administrador autoriza traslado (#7, → `TRASPASO`)
→ app activa desde la PC nueva (#2, `TRASPASO`→`OCUPADO`) → la PC anterior queda sin autorización.

**Pérdida o fraude:** administrador revoca el serial (#7) → el instalador devuelve `602` y la app no verifica.

**Fin de contrato/impago:** administrador desactiva la licencia (#6) → bloquea todas sus activaciones y verificaciones.

**Puestos adicionales:** administrador agrega seriales (#6) → cada serial se activa en una PC independiente.

---

## Ejecución

```bash
# Desarrollo (H2 en memoria, sin MySQL)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Producción (MySQL 8) - ver docs/DESPLIEGUE.md
export LICENSE_HMAC_SECRET="..." ADMIN_API_KEY="..." DB_PASSWORD="..."
./mvnw spring-boot:run
```

Panel web: `http://localhost:8085/admin` (consume la API con la clave de admin).
Pruebas: `./mvnw test` (unitarias, integración y concurrencia).

## Estructura

```
com.zaga.licenciamientoapp
├── config/        propiedades tipadas + filtros (auth admin, rate limit)
├── domain/        entidades y enums (Licencia, Activador, PcInfo)
├── repository/    repositorios JPA (bloqueo pesimista en activadores)
├── service/       serial (HMAC + generador), fingerprint, offline, audit,
│                  InstallerValidationService, LicenseAdminService, ActivationService
└── web/           controladores REST, DTOs y manejo global de errores
```

## Correspondencia con los entregables

| # | Entregable | Ubicación |
|---|---|---|
| 1 | DDL MySQL 8 (3 tablas) | `src/main/resources/db/schema-mysql.sql` |
| 2 | Proyecto backend Spring Boot | raíz del proyecto |
| 3 | Entidad/repositorio/servicio/controlador | `domain`, `repository`, `service`, `web` |
| 4 | Endpoint validación Advanced Installer | `web/InstallerController` |
| 5 | Endpoint activación agnóstico | `web/ActivationController` |
| 6 | Servicio HMAC de seriales | `service/serial/HmacSha256Service` |
| 7 | Servicio de identificación extensible | `service/fingerprint/*` |
| 8 | Servicio transaccional activación/traslado | `service/ActivationService` |
| 9 | Operaciones administrativas protegidas | `web/AdminController` + `config/AdminAuthFilter` |
| 10 | Ejemplos de solicitudes HTTP | `docs/PROTOCOLO.md`, `requests/licenciamiento.http` |
| 11 | Pruebas unitarias/integración/concurrencia | `src/test/java/...` |
| 12 | Guía de despliegue y conexión con AI | `docs/DESPLIEGUE.md` |
| 13 | Documentación del protocolo | `docs/PROTOCOLO.md` |

## Limitaciones documentadas

La identificación por huella de hardware reduce el uso accidental o no autorizado de un
serial en otros equipos, pero **no** garantiza una identidad imposible de falsificar. La
validación del instalador no sustituye la activación ni la protección del software.
Mientras un serial esté en `TRASPASO`, cualquier equipo que conozca el serial podría
reclamarlo (se acepta como riesgo operativo del modelo sin tokens).
