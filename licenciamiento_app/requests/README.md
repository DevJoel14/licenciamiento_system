# Requests de prueba (IntelliJ HTTP Client)

Archivo ejecutable: [`licenciamiento.http`](licenciamiento.http).

## Como usarlo en IntelliJ IDEA

1. Abre `requests/licenciamiento.http`.
2. Aparece un icono ▶ junto a cada request. Ejecutalo con **Run**.
3. Ejecuta **primero el request #1** (`Crear licencia`): captura automaticamente
   `serial`, `activadorId` y `licenciaId` como variables globales, de modo que los
   demas requests se encadenan sin editar nada.
4. Los resultados aparecen en el panel *Services* / *Run* a la derecha.

> Variables por defecto: `base=http://localhost:8085`, `adminKey=dev-admin-key-change-me`.
> Si definiste `ADMIN_API_KEY` en el entorno, cambia `@adminKey` en el archivo.

## Orden recomendado

| Paso | Request | Resultado esperado |
|---|---|---|
| 1 | #1 Crear licencia | `201/200`, guarda `serial` |
| 2 | #2 Validar instalador | `601` |
| 3 | #2b Producto incorrecto | `602` |
| 4 | #3 Activar equipo A | `exitoso: true`, `OCUPADO` |
| 5 | #4 Verificar mismo equipo | `exitoso: true` |
| 6 | #5 Activar equipo B | HTTP 409 `EQUIPO_NO_AUTORIZADO` |
| 7 | #6 Estado licencia | `totalSeriales: 2` |
| 8 | #8 Autorizar traslado | `TRASPASO` |
| 9 | #9 Activar equipo B | `OCUPADO` (traslado completado) |
| 10 | #10 Revocar serial | `REVOCADO` |
| 11 | #10b Validar tras revocar | `602` |
| 12 | #11 Agregar puestos | 3 seriales nuevos |
| 13 | #12 Desactivar licencia | `DESACTIVA` |
| 14 | #12b Validar desactivada | `602` |
| 15 | #13 Admin sin key | HTTP 401 |
| 16 | #14 Clave publica | PEM de la clave publica |

## Notas

- Los endpoints de instalador y activacion **no** usan `X-Admin-Api-Key`; por eso
  aparecen sin esa cabecera.
- El `serial` solo se muestra al crear la licencia. En la base de datos solo se guarda
  su HMAC, por lo que no puede recuperarse despues.
- Mientras el serial este en `TRASPASO`, cualquier equipo que lo conozca podria
  reclamarlo; en produccion autoriza el traslado solo tras verificar la solicitud.
