# Resumen — `final`

Commits: frontend `2cd0305`, backend `f5a0bc5`

## Entrega

| Escenario | n | Entregadas | % | IC 95 % (Wilson) | Mediana (s) | p90 (s) | Errores del runner |
|---|--:|--:|--:|---|--:|--:|--:|
| S0 | 50 | 50 | 100.0 % | [92.9 %; 100.0 %] | 2.7 | 4.6 | 0 |
| S1a | 50 | 50 | 100.0 % | [92.9 %; 100.0 %] | 5.4 | 5.5 | 0 |
| S1b | 50 | 50 | 100.0 % | [92.9 %; 100.0 %] | 5.3 | 5.5 | 0 |
| S2 | 50 | 50 | 100.0 % | [92.9 %; 100.0 %] | 5.3 | 5.5 | 0 |
| S3 | 50 | 50 | 100.0 % | [92.9 %; 100.0 %] | 492.0 | 493.5 | 0 |
| S4 | 50 | 50 | 100.0 % | [92.9 %; 100.0 %] | 0.4 | 0.5 | 0 |
| S5 | 50 | 50 | 100.0 % | [92.9 %; 100.0 %] | -715.9 | -715.5 | 0 |
| S6 | 50 | 50 | 100.0 % | [92.9 %; 100.0 %] | 3.0 | 6.3 | 0 |
| S7 | 50 | 50 | 100.0 % | [92.9 %; 100.0 %] | 5.4 | 5.5 | 0 |

Tiempo hasta la entrega = max(visible en pantalla, `READ`) − vuelta de la conexión (en S0, − t₀; en S4, − apertura de la página nueva).

## Camino de entrega

| Escenario | sse | history | retry_sse | sin llegada |
|---|--:|--:|--:|--:|
| S0 | 45 | 5 | 0 | 0 |
| S1a | 0 | 50 | 0 | 0 |
| S1b | 0 | 50 | 0 | 0 |
| S2 | 0 | 50 | 0 | 0 |
| S3 | 46 | 4 | 0 | 0 |
| S4 | 0 | 50 | 0 | 0 |
| S5 | 0 | 50 | 0 | 0 |
| S6 | 2 | 48 | 0 | 0 |
| S7 | 0 | 50 | 0 | 0 |

## Duplicados, cobros y bloqueo

| Escenario | Dup. transporte (pruebas / eventos extra) | Dup. pantalla | Dup. BD | Cobros dobles | Violaciones de bloqueo | Control de bloqueo |
|---|---|--:|--:|--:|--:|---|
| S0 | 0 / 0 | 0 | 0 | 0 | 0 | blocked: 49, skipped_already_read: 1 |
| S1a | 0 / 0 | 0 | 0 | 0 | 0 | blocked: 50 |
| S1b | 0 / 0 | 0 | 0 | 0 | 0 | blocked: 50 |
| S2 | 0 / 0 | 0 | 0 | 0 | 0 | blocked: 50 |
| S3 | 50 / 100 | 0 | 0 | 0 | 0 | blocked: 50 |
| S4 | 0 / 0 | 0 | 0 | 0 | 0 | blocked: 50 |
| S5 | 0 / 0 | 0 | 0 | 0 | 0 | blocked: 50 |
| S6 | 0 / 0 | 0 | 0 | 0 | 0 | blocked: 50 |
| S7 | 0 / 0 | 0 | 0 | 0 | 0 | not_applicable: 50 |

`subscription_delta` incluye el cobro de una consulta de control aceptada después de `READ`; en ese caso se revisa `notes`.

## Estado final del outbox

| Escenario | READ | PUBLISHED | PENDING | sin evento | attempt_count (mín–máx) |
|---|--:|--:|--:|--:|---|
| S0 | 50 | 0 | 0 | 0 | 0–1 |
| S1a | 50 | 0 | 0 | 0 | 1–1 |
| S1b | 50 | 0 | 0 | 0 | 2–2 |
| S2 | 50 | 0 | 0 | 0 | 2–2 |
| S3 | 50 | 0 | 0 | 0 | 3–3 |
| S4 | 50 | 0 | 0 | 0 | 2–2 |
| S5 | 50 | 0 | 0 | 0 | 0–0 |
| S6 | 50 | 0 | 0 | 0 | 1–1 |
| S7 | 0 | 0 | 0 | 50 | — |

## Tandas (S5, S6)

| Tanda | Pruebas | Entregadas | Inicio del fallo | Fin del fallo |
|---|--:|--:|---|---|
| S6-batch-1 | 6 | 6 | 1789698756474 | 1789698774701 (18.2 s) |
| S6-batch-2 | 4 | 4 | 1789698883820 | 1789698900120 (16.3 s) |
| S6-batch-3 | 4 | 4 | 1789699010215 | 1789699026110 (15.9 s) |
| S6-batch-4 | 4 | 4 | 1789699137113 | 1789699150881 (13.8 s) |
| S6-batch-5 | 4 | 4 | 1789699263220 | 1789699277641 (14.4 s) |
| S6-batch-6 | 4 | 4 | 1789699389941 | 1789699402073 (12.1 s) |
| S6-batch-7 | 4 | 4 | 1789699510634 | 1789699526106 (15.5 s) |
| S6-batch-8 | 4 | 4 | 1789699637731 | 1789699646771 (9.0 s) |
| S6-batch-9 | 4 | 4 | 1789699753341 | 1789699762039 (8.7 s) |
| S6-batch-10 | 4 | 4 | 1789699869186 | 1789699877820 (8.6 s) |
| S6-batch-11 | 4 | 4 | 1789699984941 | 1789699994369 (9.4 s) |
| S6-batch-12 | 4 | 4 | 1789700101006 | 1789700110802 (9.8 s) |
| S5-batch-1 | 46 | 46 | 1789700213808 | 1789700937160 (723.4 s) |
| S5-batch-2 | 4 | 4 | 1789701022254 | 1789701744544 (722.3 s) |
