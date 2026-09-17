# Registro de defectos — inyección de fallos en la entrega de respuestas

Estado al 2026-09-17: **humo S0–S4 hecho, piloto sin empezar.** Nada corregido todavía (§6: primero se confirma en
el piloto). Resultados del humo en `results/humo/`.

## Candidatos observados en el humo (a confirmar con el piloto, n = 5)

### D1 — H1: el frontend no vigila los heartbeats (S2)

- **Síntoma:** en `blackhole` el stream SSE queda `connected` y mudo; tras volver la red no hay reconexión. S2-001: no
  entregada en 42 min, outbox `PENDING` con 5 intentos, envío bloqueado todo ese tiempo.
- **Causa:** `frontend/src/hooks/useChat.js`, bucle `await reader.read()` del efecto SSE: sin timeout de inactividad.
- **Corrección prevista:** watchdog de ~45 s (3 heartbeats) que corta el stream y reconecta por el camino de backoff.

### D2 — el vigilante de `processing-status` nunca reconcilia (S2)

- **Síntoma:** traza de red de la depuración de S2: vuelve la red, `processing-status` → 200 con `processing:false`,
  el polling se detiene y **nunca** se pide `/messages`.
- **Causa:** `useChat.js`, `refreshProcessing`: `loadProcessingStatus` escribe `processing:false` en el store, zustand
  re-renderiza en síncrono, el cleanup del efecto pone `disposed = true` y la guarda `if (disposed || …) return`
  sale antes de `reconcileRef.current(...)`.
- **Corrección prevista:** no condicionar la reconciliación a `disposed` cuando se observa la transición.

### D3 — posible cierre de sesión al reabrir con el access token vencido (S4)

- **Síntoma:** S4-001 no entregada. La página nueva (15 min, access token de 15 min vencido) recibe 401 en varias
  peticiones a la vez, `/auth/refresh` → 200, pero los dos `subscribe` terminan `ERR_ABORTED` y la página carga las
  imágenes de la landing: todo indica que la app hizo `logout()` y navegó a `/`. Outbox `PENDING` con 5 intentos.
- **Hipótesis, sin verificar:** carrera entre el refresh de `useChat` (`refreshAccessToken` en el 401 del subscribe) y
  el interceptor de axios; si el backend rota el refresh token, el segundo refresh falla y dispara `logout()`.
  Revisar `services/api.js` (`isRefreshing` se comparte, pero el 401 del fetch del SSE entra por otro camino) y la
  rotación en `AuthService.refresh`.

## No ejercitados por los escenarios

- **H2** (`assistant_error` fuera del outbox): el mock siempre responde bien, así que ningún escenario lo dispara.
  Confirmado solo por lectura de código (`ChatQueuedMessageService.persistAndDispatchFailure`).
- **H3** (`RETRY_DELAY` fijo en los listeners): mismo valor que la propiedad (10 min); sin diferencia medible.

## Commits

- Humo: corrido sobre cambios sin commitear (frontend base `eab4a1e`, backend base `dd460e2`); `results/humo/`
  registra los ids de commits que se deshicieron después. Congelar commits antes de la final, con permiso de Piero.
