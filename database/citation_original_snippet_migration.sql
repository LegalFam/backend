-- El agente XAI devuelve dos textos por cita: el resumen que lee el usuario
-- (source_snippet) y el pasaje literal del documento en el que se apoyo. De ese pasaje
-- sale la ubicacion del articulo, asi que guardarlo es lo que permite auditar despues
-- por que una cita quedo atribuida a un articulo y no a otro.
--
-- Las citas anteriores a este cambio se quedan en NULL: no hay de donde recuperarlas.
ALTER TABLE citations
    ADD COLUMN IF NOT EXISTS source_original_snippet TEXT NULL;
