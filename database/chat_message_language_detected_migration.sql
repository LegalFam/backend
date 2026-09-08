-- El idioma de un turno pasa de declararse a detectarse.
--
-- `language` siempre fue el que mandaba el frontend, que es la preferencia de interfaz y no
-- una lectura del texto: alguien con la app en quechua puede escribir en aymara y se le
-- respondia en quechua. Ahora el flujo n8n lee la lengua del propio mensaje y esa es la que
-- queda en `language`.
--
-- `language_requested` guarda lo que habia pedido la interfaz, y SOLO cuando difiere del
-- idioma efectivo. NULL significa que no hubo desajuste, que es el caso normal. Se persiste
-- en vez de calcularse en pantalla porque el idioma de la interfaz cambia y el del mensaje
-- no: sin esta columna, recargar el historial haria desaparecer el aviso que explica por que
-- ese turno esta en otra lengua.
--
-- Las filas anteriores quedan validas sin migracion de datos: NULL en todas.
ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS language_requested VARCHAR(8) NULL;

ALTER TABLE chat_message
    DROP CONSTRAINT IF EXISTS chat_message_language_requested_check;

ALTER TABLE chat_message
    ADD CONSTRAINT chat_message_language_requested_check
    CHECK (language_requested IS NULL OR language_requested IN ('es', 'qu', 'ay'));
