-- Soporte de quechua sureno y aymara en la orientacion legal.
--
-- La traduccion ocurre en los bordes del pipeline: el razonamiento juridico, el corpus y
-- las citas siguen siendo integramente en espanol. Por eso `content` mantiene siempre el
-- espanol canonico y el texto en la lengua del usuario vive aparte, en `content_localized`.
--
-- No es una decision de almacenamiento sino de control de flujo: `previous_messages` se
-- arma desde `content`, y el Clarification Gate del flujo n8n reconoce si ya pregunto
-- buscando encabezados literales en espanol dentro del turno anterior. Si el historial
-- viajara traducido, esa logica anti-bucle se romperia en silencio.
--
-- Las filas anteriores quedan validas sin migracion de datos: language='es' por defecto y
-- content_localized en NULL, con lo que el frontend cae a `content`.
ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS language VARCHAR(8) NOT NULL DEFAULT 'es';

ALTER TABLE chat_message
    DROP CONSTRAINT IF EXISTS chat_message_language_check;

ALTER TABLE chat_message
    ADD CONSTRAINT chat_message_language_check
    CHECK (language IN ('es', 'qu', 'ay'));

ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS content_localized TEXT NULL;

ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS next_steps_localized TEXT NULL;

-- Solo se traduce el resumen que redacta el agente XAI. `source_original_snippet` es el
-- pasaje literal de la norma peruana y se queda en espanol siempre: traducirlo destruiria
-- la posibilidad de contrastar la cita contra la fuente.
ALTER TABLE citations
    ADD COLUMN IF NOT EXISTS source_snippet_localized TEXT NULL;
