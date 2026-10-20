# Guía de despliegue y configuración

## 1. Requisitos

- Java 17+.
- MySQL 8 (base de datos central).
- Servidor con TLS terminado (Spring Boot con HTTPS o reverse proxy).

## 2. Base de datos MySQL 8

```sql
CREATE DATABASE licenciamiento CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'licenciamiento'@'%' IDENTIFIED BY 'CLAVE_FUERTE';
GRANT SELECT, INSERT, UPDATE, DELETE ON licenciamiento.* TO 'licenciamiento'@'%';
FLUSH PRIVILEGES;
```

Aplique el DDL:

```bash
mysql -u root -p licenciamiento < src/main/resources/db/schema-mysql.sql
```

> Usuario de MySQL con **privilegios mínimos** (sin `DROP`, `ALTER`, `GRANT`). El esquema se
> crea aparte; la aplicación arranca con `ddl-auto=validate`.

## 3. Variables de entorno (secretos fuera del código)

| Variable | Descripción | Obligatoria |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Conexión MySQL | sí (tienen valores por defecto) |
| `DB_USER`, `DB_PASSWORD` | Credenciales MySQL | sí |
| `LICENSE_HMAC_SECRET` | Secreto HMAC-SHA-256 de los seriales | **sí** |
| `ADMIN_API_KEY` | Clave de operaciones administrativas | **sí** |
| `OFFLINE_PRIVATE_KEY`, `OFFLINE_PUBLIC_KEY` | Rutas del par RSA de firma offline | solo si offline habilitado |
| `SERVER_PORT` | Puerto (por defecto 8085) | no |

Genera secretos fuertes, por ejemplo:

```bash
openssl rand -base64 48   # LICENSE_HMAC_SECRET
openssl rand -base64 32   # ADMIN_API_KEY
```

## 4. HTTPS

**Opción A — TLS en Spring Boot** (`src/main/resources/application-prod.properties`):

```properties
server.ssl.enabled=true
server.ssl.key-store=file:/etc/licencias/keystore.p12
server.ssl.key-store-password=${KEYSTORE_PASSWORD}
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=licencias
```

**Opción B — reverse proxy (recomendado)** con Nginx/Caddy terminando TLS y reenviando a
`http://127.0.0.1:8085`. Configure `X-Forwarded-For` para que el limitador de tasa identifique
la IP real. Fuerce `HTTPS` y redirección 301 desde HTTP.

## 5. Arranque

```bash
export DB_HOST=... DB_USER=licenciamiento DB_PASSWORD=... \
       LICENSE_HMAC_SECRET=... ADMIN_API_KEY=...
java -jar target/LicenciamientoApp-0.0.1-SNAPSHOT.jar
# o perfiles: --spring.profiles.active=prod,dev
```

Desarrollo local sin MySQL:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## 6. Conexión con Advanced Installer

Advanced Installer valida el serial mediante **validación server-side** (método **POST**,
`application/x-www-form-urlencoded`). Pasos en *Licensing → Serial Validation*:

1. Marca **Use Serial Validation**.
2. Método de validación: **Server-side validation**.
3. **URL:** `https://licencias.empresa.com/api/instalador/validar`.
4. **Template** acorde a tu formato de serial: `<?????-?????-?????-?????-?????>` (5 grupos de 5 alfanuméricos).
5. Propiedad **`ADD_SV_INFO`** = `SISTEMA_TALLER` (el código de producto). Este valor llega al backend en el campo `ai`.
6. (Opcional) Restringir la validación a la etapa de diálogos para no repetirla en la copia de archivos.

Campos que Advanced Installer envía por POST:

| Campo | Origen | Uso |
|---|---|---|
| `sn` | `PIDKEY` | serial introducido |
| `ai` | `ADD_SV_INFO` | **código de producto** |
| `username`, `company`, `email`, `version`, `languageid` | diálogo / sistema | informativos (el backend los ignora) |

Respuesta esperada por Advanced Installer: `AnswerCode\nMessage`

- `601` → permite continuar.
- `602\n<mensaje>` → detiene la instalación y muestra el mensaje.

> La validación corre **antes** de instalar (etapa de diálogos) y puede repetirse en la copia
> de archivos; este endpoint es de solo lectura e idempotente. No registra equipos, no cambia
> estados ni consume puestos. La emisión del token de activación la hace la aplicación
> (`/api/activacion/activar`); la variante con identidad de equipo del §6.1 **sí** registra la PC.
> Si la PC no tiene internet o el servidor no responde `601`/`602`, Advanced Installer bloquea la instalación.

### 6.1 Validar además la identidad del equipo (custom action)

La validación server-side **no** envía la huella de hardware. Para comprobar que se instala en el
**mismo equipo** (misma PC permite, otra PC rechaza), añade un **custom action PowerShell** que
llame a `POST /api/instalador/validar-hardware`.

1. Página **Custom Actions** → **New Custom Action Wizard** → *Run Inline PowerShell Script*.
   Sin secuencia (se dispara desde el diálogo).
2. **Execution Options:** *Immediate* + **"Wait for custom action to finish before proceeding"**.
3. Propiedades (Properties page): `PC_OK` (default `0`) y `PC_MSG`. **No** uses prefijos `AI_`
   (reservados por Advanced Installer).
4. Script:

```powershell
#Requires -version 3
Param()

Function Send-PcData() {
  [string] $serial = Get-Property -name "PIDKEY"

  $mb  = (Get-CimInstance Win32_BaseBoard).SerialNumber
  $cpu = (Get-CimInstance Win32_Processor).ProcessorId
  $mac = (Get-CimInstance Win32_NetworkAdapter -Filter "PhysicalAdapter=True" |
          Select-Object -First 1).MACAddress

  $body = @{
    serial              = $serial
    codigoProducto      = "SISTEMA_TALLER"
    instalacionId       = $env:COMPUTERNAME
    componentesHardware = @("MB:$mb", "CPU:$cpu", "MAC:$mac")
  } | ConvertTo-Json

  try {
    $r = Invoke-RestMethod -Uri "https://licencias.empresa.com/api/instalador/validar-hardware" `
           -Method Post -ContentType "application/json" -Body $body -TimeoutSec 15
    Set-Property -name "PC_OK"  -value $(if ($r.permitido) { "1" } else { "0" })
    Set-Property -name "PC_MSG" -value $r.mensaje
    Write-Output ("Validacion PC => codigo=" + $r.codigo + " permitido=" + $r.permitido)
  }
  catch {
    Set-Property -name "PC_OK"  -value "0"
    Set-Property -name "PC_MSG" -value "No se pudo validar la PC con el servidor."
    Write-Output ("Validacion PC FALLO: " + $_)
  }
}

Send-PcData
```

5. **Dialog Editor → `UserRegistrationDlg` → botón `[Next]` → Published Events**, en orden:

| Orden | Evento | Condición |
|---|---|---|
| 1 | *(nativo)* validación del serial | 1 |
| 2 | *Execute custom action* → `SendPcData` | 1 |
| 3 | *NewDialog* (siguiente diálogo) | `PC_OK = "1"` |
| 4 | *Display error dialog* (muestra `[PC_MSG]`) | `PC_OK = "0"` |

> El bloqueo del avance lo hace la **condición** del evento, no un `MessageBox`.
>
> **Importante — etapa de ejecución:** **no** añadas este custom action a la *Install Execution
> Stage* leyendo `PIDKEY`. Para cuando corre la secuencia de ejecución, Advanced Installer ya vació
> `PIDKEY`, así que el script enviaría el serial en blanco y el servidor respondería
> `DATOS_INVALIDOS` (el instalador abortaría en un equipo válido). La validación de la PC debe
> correr **solo en la etapa de diálogos** (evento `[Next]`).
>
> Si necesitas instalación silenciosa (`/qn`), copia el serial a una propiedad segura *antes* de
> que se vacíe: en `[Next]` con orden previo publica `SN_HOLD = [PIDKEY]`, añade `SN_HOLD` a
> *Secure Custom Properties* y lee esa propiedad en el script (con respaldo en `PIDKEY`). Recién
> entonces programa el custom action en ambas secuencias.
>
> **Efecto en la base de datos:** este custom action **sí escribe**. Si el serial está `SIN_USO`,
> el servidor crea la fila de `PC_INFO`, guarda la huella y pasa el activador a `OCUPADO`; si ya
> está `OCUPADO` con la misma huella, refresca las marcas de uso. Como corre en el `[Next]` del
> diálogo (antes de copiar archivos), **cancelar la instalación después de validar deja el serial
> ocupado**.

## 7. Endurecimiento recomendado

- TLS obligatorio; redirigir todo HTTP a HTTPS.
- Secreto HMAC y `ADMIN_API_KEY` rotables y almacenados en un gestor de secretos.
- Rate limit activo (`licensing.rate-limit.enabled=true`); ajustar capacidad/ventana.
- Logs sin secrets ni seriales completos (los seriales se enmascaran: `****XXXX`).
- Firewall: exponer solo el puerto TLS; la base de datos en red privada.
- Backups de MySQL y del par de claves offline (`keys/`), fuera del control de versiones.
- La autorización local (token offline) es una ventana de gracia, no protección definitiva.

## 8. Observabilidad y auditoría

Los eventos administrativos y de activación se emiten al logger `AUDIT`
(`op=... actor=... detalle=...`). Rediríjalo a su sistema de auditoría (p.ej. appender a
Elastic/SIEM) para conservar quién autorizó cada traslado y cuándo.

## 9. Pruebas

```bash
./mvnw test
```

Incluye:
- Unitarias: HMAC, generador de seriales, huella de hardware.
- Integración (MockMvc): flujo completo, autenticación admin, serial inexistente.
- Concurrencia: N equipos reclamando el mismo serial `SIN_USO` → exactamente uno gana.
