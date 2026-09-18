# Registro de defectos — inyección de fallos en la entrega de respuestas

Estado al 2026-09-18: **cerrado.** Cuatro defectos corregidos (D1–D3 del frontend en `main` `02aac69`, D4 del
backend en `main` `7a64961`), traídos a esta rama por merge, y **corrida final completa: 450/450 entregadas** (S0–S7,
n = 50) sin defectos nuevos. Commits congelados y cifras al final de este archivo; reporte completo en
`07_Reporte_O9.md` de la carpeta del paper. Resultados en `results/humo/`, `results/piloto/`, `results/piloto-2/`,
`results/piloto-3/` (repeticiones) y `results/final/`.

**Repetición del piloto con las correcciones:** S2 **5/5** entregadas por historial 5,1 s después de volver la red
(antes 0/5) y S4 **5/5** por historial 0,6 s después de abrir la página nueva (antes 0/5), sin duplicados en
pantalla ni en base de datos y con un solo cobro. S5 y S6 se repiten con tandas de 4 (ver la observación del
ejecutor); la primera repetición salió nula por dos fallos del runner, ya corregidos: una prueba caída dejaba a la
tanda esperando y el fallo se anclaba a los t₀ reportados en vez de al instante en que el mock responde.

Tests: `frontend/src/hooks/useChat.test.jsx` (Vitest, `npm test`). Con las correcciones pasan 4/4; sin ellas fallan
los tres tests de defecto y pasa el de regresión (el stream no se corta mientras llegan heartbeats).

Los tests de D2 y D3 corren con `IS_REACT_ACT_ENVIRONMENT = false`: dentro de `act()` React difiere los renders
que dispara el store y la carrera con la limpieza del efecto no ocurre, así que esos tests pasaban también sin la
corrección.

## D1 — H1: el frontend no vigila los heartbeats

- **Escenarios:** S2. Humo 0/1, piloto **0/5** (S2-001 a S2-005).
- **Síntoma:** en `blackhole` el stream SSE queda `connected` y mudo; tras volver la red no hay reconexión en los
  30 min de observación. Outbox `PENDING` con 5 intentos, respuesta invisible y envío bloqueado todo ese tiempo.
- **Causa:** `frontend/src/hooks/useChat.js`, efecto SSE: `await reader.read()` sin timeout de inactividad.
- **Corrección:** watchdog por conexión (`SSE_IDLE_TIMEOUT_MS = 45000`, tres heartbeats de 15 s, revisado cada 5 s)
  que aborta el stream y marca `stale`; el `catch` reconecta por el backoff si el aborto vino del watchdog. Cubre
  también un `subscribe` que se queda colgado.
- **Test:** `reconnects when the stream stays silent past the heartbeat window` y, de regresión, `keeps the stream
  while heartbeats keep arriving`.
- **Commit:** `frontend main 02aac69`. **Piloto repetido:** S2 5/5. **Final:** S2 50/50.

## D2 — el vigilante de `processing-status` nunca reconcilia

- **Escenarios:** S2 y S5. En S2 queda tapado por D1. En S5 se ve solo: humo S5-001, la respuesta estaba persistida
  desde t₀ y `processing` en false, pero no apareció en pantalla hasta el reintento del worker, 20 min después.
- **Síntoma:** traza de red: `processing-status` → 200 con `processing:false`, el polling se detiene y nunca se pide
  `/messages` ni `/payments/subscription`.
- **Causa:** `useChat.js`, `refreshProcessing`: `loadProcessingStatus` escribe `processing:false` en el store; la
  actualización de zustand se renderiza en una microtarea, el cleanup del efecto pone `disposed = true`, y la guarda
  `if (disposed || …) return` sale antes de `reconcileRef.current(...)`. El vigilante solo podía reconciliar en la
  vuelta en que detecta el cambio, y es justo la vuelta que lo desmonta.
- **Corrección:** `disposed` se mira antes de pedir el estado y no después.
- **Test:** `reloads the history when the server stops processing`.
- **Commit:** `frontend main 02aac69`. **Piloto repetido:** S5 4/4 por historial. **Final:** S5 50/50 por historial,
  6,2 s después de parar Rabbit (antes, ~20 min por el reintento del worker).

## D3 — cierre de sesión al reabrir el chat con el access token vencido

- **Escenarios:** S4. Humo 0/1, piloto **0/5**; en las cinco la página nueva termina en la landing.
- **Síntoma:** la página nueva (15 min, access token de 15 min vencido) recibe 401, `/auth/refresh` → 200 (uno
  solo), los dos `subscribe` terminan `ERR_ABORTED` y la app navega a `/` sin sesión. Outbox `PENDING`.
- **Causa:** `useChat.js`, efecto SSE: el subscribe recibe 401 → `refreshAccessToken()`. El refresh llama a
  `setTokens`, cambia `accessToken`, el efecto se limpia y aborta `controller`. El `fetch` de reintento usa ese
  `controller` abortado, lanza `AbortError`, y el `catch` lo trata como refresh fallido: `logout()` + `navigate('/')`.
  Cualquier usuario que vuelva al chat con el access token vencido y el refresh token válido queda deslogueado.
- **Corrección:** si `controller.signal.aborted`, el error se relanza al `catch` exterior, que sale sin cerrar
  sesión (el efecto nuevo ya reconecta con el token nuevo). `logout` queda solo para un refresh que falla de verdad.
- **Test:** `does not log out when the token refresh restarts the stream`.
- **Commit:** `frontend main 02aac69`. **Piloto repetido:** S4 5/5. **Final:** S4 50/50.

## D4 — H3: retardo de reintento escrito en los listeners

- **Escenarios:** ninguno lo hace fallar; el valor escrito (10 min) coincide con la propiedad, así que no hay
  diferencia medible.
- **Causa:** `Rabbit…DeliveryListener` y `Local…DeliveryListener` tenían `RETRY_DELAY` fijo en el código en vez de
  leer `app.chat.outbox.relay.retry-delay-ms`. Cambiar la propiedad no cambiaba el reintento.
- **Corrección:** los listeners leen la propiedad.
- **Test:** 2 nuevos en el backend (279 en verde).
- **Commit:** `backend main 7a64961`.

## Sin defecto en el piloto

- S0, S1a, S1b, S3: 5/5 entregadas cada uno, 0 duplicados en pantalla y en base de datos, 0 cobros dobles, bloqueo
  `blocked` en las 20. S3: 3 eventos SSE del mismo id por prueba (duplicado de transporte esperado), 1 copia.

## Observación, no defecto de entrega: el backend atiende 4 respuestas a la vez (corregido después de la final, ver abajo)

`chatTaskExecutor` (`AsyncConfig`) tiene `corePoolSize=4`, `maxPoolSize=8` y una cola de 200. Un pool de Spring solo
crea hilos por encima del núcleo cuando la cola está **llena**, así que con esa cola los 8 hilos nunca se usan: se
procesan 4 llamadas al agente a la vez y el resto espera en cola. Se vio en la repetición del piloto: con 5 sesiones
simultáneas, la quinta llamó al n8n simulado 89 s después de enviarse, justo al liberarse un hilo.

No afecta a la entrega —el mensaje espera, no se pierde— pero sí al tiempo hasta la respuesta con carga: con la
latencia real del agente (mediana 42,5 s) la quinta consulta simultánea empieza a atenderse recién después de la
primera. No se corrige aquí: la spec manda dejar la configuración como en producción. Por eso las tandas de S5 y S6
son de **4** sesiones y no de 10: con 4 hilos nunca hay 10 respuestas en vuelo a la vez que un corte pueda alcanzar.

## D5 — H2: `assistant_error` fuera del outbox (corregido después de la final)

- **Escenario:** S7 (nuevo, fuera de la spec): el mock falla (`POST /fail`) mientras la conexión está cortada 30 s.
- **Medido en la final:** 0/50 errores llegan en vivo (0 eventos SSE de error); 50/50 se ven al reconectar, por
  historial, 5,4 s después (mediana). Sin evento de outbox, sin receipt, sin reintento, sin bloqueo y sin cobro.
- **Causa:** `ChatQueuedMessageService.persistAndDispatchFailure` despachaba el error directo, sin outbox.
- **Corrección:** `persistAssistantFailure` guarda el mensaje de sistema y su evento de outbox en la misma
  transacción; los listeners despachan mensaje o error según el evento (`IChatAssistantDeliveryPort.dispatch`); el
  receipt acepta mensajes de sistema; el frontend confirma la lectura de los errores. El error queda con la misma
  garantía que la respuesta: reintento, receipt y bloqueo de la sesión hasta confirmarlo.
- **Tests:** backend 281 en verde (`persistAssistantFailureEnqueuesErrorInOutbox`,
  `rabbitListenerDispatchesQueuedAssistantErrorAsError`); frontend 5 en verde (`confirms the receipt of an assistant
  error`, que falla sin la corrección).
- **Commits:** `backend main f594383`, `frontend main a730cda`.
- **Verificación** (`results/post-fix-h2/`, S7, S0 y S1a con n = 3): 9/9 entregadas; los 3 errores de S7 con evento de
  outbox en `READ` al primer intento.
- **Ojo al desplegar:** primero el frontend. Un backend nuevo con el frontend viejo deja la sesión bloqueada tras un
  error, porque el frontend viejo no confirma los mensajes de sistema.

## D6 — el backend muere con la llamada al agente en curso: usuario bloqueado para siempre

- **Escenario:** S8 (nuevo): tandas de 8; el backend se mata con `taskkill /F` 5 s antes de que el mock responda y se
  rearranca con el mismo jar.
- **Piloto sin corrección** (`results/piloto-s8/`, ventana de 10 min): **0/4**. Las cuatro quedan en `PROCESSING` sin
  mensaje, y el usuario no puede volver a enviar en ninguna sesión (`ChatSendPolicy` rechaza con un procesamiento
  activo). En Cloud Run basta un despliegue o una bajada de instancias con una consulta en curso.
- **Causa:** `ChatMessageQueuedEvent` es un evento de Spring en memoria (también con Rabbit activo) y nada recupera los
  procesamientos que quedan abiertos al morir la instancia.
- **Corrección:** `ChatStaleProcessingSweeper`, cada minuto, cierra los procesamientos `QUEUED`/`PROCESSING` sin cambios
  desde hace más del timeout de n8n + 1 min (6 min con `app.n8n.timeout-ms=300000`): pasado ese plazo ninguna instancia
  puede seguir con ellos. Los cierra con `persistAssistantFailure` (`upstream_timeout`), que ahora marca el
  procesamiento bajo bloqueo **antes** de crear el mensaje, así que dos instancias no generan dos errores. El error va
  por el outbox (D5), no se cobra, y el usuario puede volver a preguntar.
- **Tests:** backend 283 en verde (`ChatStaleProcessingSweeperTest`, `persistAssistantFailureSkipsMessagesAlreadyFinished`).
- **Commit:** `backend main db3539e`.
- **Piloto con la corrección** (`results/piloto-s8-fix/`): **4/4**, error visible ~5 min después del rearranque, outbox
  `READ`, 1 copia en pantalla, 0 cobros, ningún usuario bloqueado.
- **Final** (`results/final-s8/`, n = 50 en 7 tandas de 8, backend `ec0fdd3`): **50/50**, IC 95 % [92,9 %; 100 %].
  Error visible 301,8 s después del rearranque (mediana; rango 301–303), outbox `READ` al primer intento, 1 copia en
  pantalla, 1 solo mensaje de sistema por sesión, 0 cobros, 0 usuarios bloqueados.
- **Lo que no hace:** no reintenta la consulta; el usuario ve «tiempo agotado» y la vuelve a enviar.

## Pool del backend (corregido después de la final)

`AsyncConfig` pasa a `corePoolSize=8` (mismo commit que D5). Verificado: con 9 sesiones simultáneas, 8 llamadas al
agente arrancan juntas y la novena 20 s después (una vuelta del mock); antes eran rondas de 4.

## Commits

- Humo: corrido sobre cambios sin commitear (frontend base `eab4a1e`, backend base `dd460e2`); `results/humo/`
  registra los ids de commits que se deshicieron después.
- Piloto S0–S6: frontend `525af54`, backend `80d1826` (runner con cambios sin commitear en `experiments/`).
- **Final (congelados):** frontend `2cd0305`, backend `f5a0bc5`. Resultados en `results/final/` (commit `d8642b6`).

## Corrida final

450/450 entregadas (IC 95 % [92,9 %; 100 %] por escenario), 0 duplicados en pantalla y en base de datos, 0 cobros
dobles, 0 violaciones del bloqueo; S3 con los 2 eventos SSE extra esperados por prueba. Tablas en
`results/final/summary.md`. Se corrió en varias invocaciones el 17 y 18 sep con los mismos commits; un tramo y una
tanda de S5 cortados a mano no dejaron filas y se repitieron enteros.
