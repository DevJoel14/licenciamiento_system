# Protocolo de licenciamiento

Contrato HTTP que debe cumplir cualquier cliente (Java, C#, C++, Kotlin, ...).
Transporte: **HTTPS**. Formato de datos: **JSON (UTF-8)** salvo la validación del instalador,
que devuelve **texto plano**.

Base URL de ejemplo: `https://licencias.empresa.com`.

---

## 1. Validación del instalador (Advanced Installer)

Endpoint de solo lectura. **No** registra computadoras, **no** cambia estados, **no** consume
puestos, **no** autoriza traslados, **no** sobrescribe huellas. Repetirlo no altera la base.

```
POST /api/instalador/validar
Content-Type: application/x-www-form-urlencoded

sn=ABCDE-FGHIJ-KLMNO-PQRST&ai=SISTEMA_TALLER
```

Advanced Installer envía `sn` (serial) y `ai` (código de producto, vía `ADD_SV_INFO`).
Por compatibilidad el endpoint también acepta `serial`/`producto` (y query string).

Respuesta (texto plano):

| Cuerpo | Significado |
|---|---|
| `601` | Permitir la instalación |
| `602` | Rechazar la instalación |
| `602\n<motivo>` | Rechazo mostrando el motivo al usuario |

Los códigos `601`/`602` **no** son códigos HTTP (la respuesta es HTTP 200). Son configurables
en `licensing.installer.allow-code` / `licensing.installer.deny-code` y deben coincidir con la
configuración del instalador.

Verificaciones del servidor: el serial existe → corresponde al producto → licencia `ACTIVA` →
serial no `REVOCADO`. Cualquier fallo devuelve `602`.

### 1.b Validación del instalador con identidad de equipo (custom action)

Variante **JSON** del mismo validador, pensada para un **custom action** de Advanced Installer
(PowerShell). Además del serial y el producto, recibe la **huella de hardware** y comprueba que
la instalación ocurre en el **mismo equipo** registrado en `PC_INFO`.

```
POST /api/instalador/validar-hardware
Content-Type: application/json
```

```json
{
  "serial": "ABCDE-FGHIJ-KLMNO-PQRST",
  "codigoProducto": "SISTEMA_TALLER",
  "instalacionId": "PC-CLIENTE",
  "versionHuella": 1,
  "componentesHardware": ["MB:...", "CPU:...", "MAC:..."]
}
```

> Alternativa: enviar `huellaPcHash` (64 hex) ya calculada y omitir `componentesHardware`.

Respuesta (JSON):

```json
{ "permitido": true,  "codigo": "601", "motivo": null, "mensaje": "Equipo autorizado." }
{ "permitido": false, "codigo": "602", "motivo": "EQUIPO_NO_AUTORIZADO",
  "mensaje": "El serial ya esta asociado a otra computadora." }
```

Regla de identidad (misma PC permite, otra PC rechaza):

| Estado del activador | Huella recibida | Resultado |
|---|---|---|
| `SIN_USO` (sin `PC_INFO`) | cualquiera | `permitido` y se **registra la PC** (`OCUPADO`) |
| `OCUPADO` / `TRASPASO` | igual a la registrada | `permitido` |
| `OCUPADO` | distinta | `EQUIPO_NO_AUTORIZADO` |
| `REVOCADO` / licencia `DESACTIVA` | cualquiera | rechazo |

Al validar, el endpoint **registra el uso del serial**: si el activador está `SIN_USO` crea la fila de
`PC_INFO`, guarda la huella y pasa el activador a `OCUPADO`; si ya está `OCUPADO` con la misma huella
lo permite y refresca las marcas de uso. No emite token de activación (eso sigue en
`POST /api/activacion/activar`).

> Un cuerpo ausente o con **JSON mal formado** responde `HTTP 400` (`tipo: "VALIDACION"`), no `500`.
> El servidor además registra en log una línea enmascarada por petición:
> `producto=… serial=****XXXX componentes=N -> MOTIVO`.

---

## 2. Activación

```
POST /api/activacion/activar
Content-Type: application/json
```

```json
{
  "serial": "ABCDE-FGHIJ-KLMNO-PQRST",
  "codigoProducto": "SISTEMA_TALLER",
  "instalacionId": "3f8c1c2e-1a2b-4c3d-9e8f-0123456789ab",
  "versionHuella": 1,
  "nombreEquipo": "PC-CONTABILIDAD",
  "sistemaOperativo": "Windows 11 Pro",
  "componentesHardware": [
    "cpu:BFEBFBFF000806E9",
    "board:HP-85A2-SERIAL",
    "mac:001122334455"
  ]
}
```

- `instalacionId`: UUID **persistente** que el cliente genera y guarda (no es prueba de identidad por sí solo).
- `componentesHardware`: identificadores admitidos por la política de privacidad del producto.
  **Nunca** el número de serie del disco. El servidor calcula la huella (orden indiferente,
  mínimo 2 componentes).
- Alternativa: enviar `huellaPcHash` (64 hex) ya calculada por el cliente y omitir los componentes.

Respuesta exitosa (`HTTP 200`):

```json
{
  "exitoso": true,
  "codigo": "ACTIVACION_OK",
  "mensaje": "Activacion correcta.",
  "estadoActivador": "OCUPADO",
  "instalacionId": "3f8c1c2e-1a2b-4c3d-9e8f-0123456789ab",
  "huellaPcHash": "9f2c...(64 hex)",
  "primeraActivacion": "2026-10-14T19:00:28.88",
  "ultimaVerificacion": "2026-10-14T19:00:28.88",
  "tokenOffline": "eyJ... .  MEUCIQ..."
}
```

Rechazo de negocio (`HTTP 409`, o `400` si los datos son inválidos):

```json
{ "exitoso": false, "tipo": "NEGOCIO", "codigo": "EQUIPO_NO_AUTORIZADO",
  "mensaje": "El serial ya esta asociado a otra computadora." }
```

Códigos de negocio: `DATOS_INVALIDOS`, `SERIAL_NO_EXISTE`, `PRODUCTO_NO_COINCIDE`,
`LICENCIA_DESACTIVA`, `SERIAL_REVOCADO`, `EQUIPO_NO_AUTORIZADO`, `HUELLA_NO_COINCIDE`,
`TRASPASO_NO_AUTORIZADO`, `SERIAL_NO_ACTIVADO`.

Comportamiento transaccional (con `SELECT ... FOR UPDATE` sobre el activador):

| Estado del activador | Resultado |
|---|---|
| `SIN_USO` | Registra PC_INFO, pasa a `OCUPADO` |
| `OCUPADO` + mismo equipo | Verificación (actualiza `ultima_verificacion`), sin duplicar |
| `OCUPADO` + otro equipo | Rechaza `EQUIPO_NO_AUTORIZADO` |
| `TRASPASO` | Actualiza PC_INFO con la nueva identidad → `OCUPADO` |
| `REVOCADO` / licencia `DESACTIVA` | Rechaza |

---

## 3. Verificación

```
POST /api/activacion/verificar
```
Mismo cuerpo que la activación. Solo es válida si el serial está `OCUPADO` y coincide la
identidad del equipo. No crea asignaciones nuevas; actualiza `ultima_verificacion`.

---

## 4. Estado de licencia (solo lectura)

```
GET /api/licencia/estado?codigoProducto=SISTEMA_TALLER&clienteRef=ACME
```

```json
{
  "exitoso": true, "licenciaId": 1, "codigoProducto": "SISTEMA_TALLER",
  "clienteRef": "ACME", "estado": "ACTIVA", "totalSeriales": 5,
  "serialesPorEstado": { "SIN_USO": 3, "OCUPADO": 2, "TRASPASO": 0, "REVOCADO": 0 }
}
```

---

## 5. Administración (requiere `X-Admin-Api-Key`)

```bash
KEY="clave-administrativa"

# Crear licencia con 3 seriales
curl -X POST https://.../api/admin/licencias \
  -H "X-Admin-Api-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"codigoProducto":"SISTEMA_TALLER","clienteRef":"ACME","cantidadSeriales":3}'

# Buscar por producto (obligatorio) y cliente (opcional), sin distinguir mayusculas
curl "https://.../api/admin/licencias?codigoProducto=SISTEMA_TALLER&clienteRef=ACME" \
  -H "X-Admin-Api-Key: $KEY"

# Detalle de una licencia (incluye sus seriales, sin valor)
curl "https://.../api/admin/licencias/1" -H "X-Admin-Api-Key: $KEY"

# Agregar 2 puestos adicionales
curl -X POST https://.../api/admin/licencias/1/seriales \
  -H "X-Admin-Api-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"cantidad":2}'

# Desactivar licencia / revocar serial
curl -X POST https://.../api/admin/licencias/1/desactivar -H "X-Admin-Api-Key: $KEY"
curl -X POST https://.../api/admin/activadores/7/revocar    -H "X-Admin-Api-Key: $KEY"

# Autorizar traslado (pasa a TRASPASO)
curl -X POST https://.../api/admin/activadores/7/traspaso \
  -H "X-Admin-Api-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"autorizadoPor":"soporte@acme"}'
```

El valor de un serial generado se devuelve **una sola vez** al crear la licencia; después solo
existe su HMAC. Guarde esos valores de forma segura.

---

## 6. Flujo de traslado (sin tokens)

1. El cliente solicita el cambio de computadora (fuera de banda: teléfono, correo, ticket).
2. El administrador verifica la solicitud.
3. El administrador llama a `POST /api/admin/activadores/{id}/traspaso` → estado `TRASPASO`.
4. El cliente activa desde la computadora nueva (`POST /api/activacion/activar`).
5. Spring Boot comprueba licencia/producto/estado, actualiza la fila de PC_INFO y pasa a `OCUPADO`.
6. El cliente no puede modificar los estados del activador.

> Riesgo aceptado: mientras el serial esté en `TRASPASO`, cualquier equipo que lo conozca podría
> reclamarlo. Autorice el traslado solo tras verificar la solicitud.

---

## 7. Token offline (autorización local)

Si `licensing.offline.enabled=true`, la activación devuelve `tokenOffline` con formato
`base64url(payload).base64url(firma RSA-SHA256)`. El cliente:

1. Descarga `/api/public-key` (una vez).
2. Verifica la firma del `payload`.
3. Comprueba que `huellaPcHash`/`instalacionId` coinciden con los suyos y que `expiraEn`
   (ventana de gracia de `licensing.offline.grace-days`) no ha pasado.

Esto permite trabajar sin conexión de forma **acotada en el tiempo**; no sustituye la
verificación en línea periódica.
