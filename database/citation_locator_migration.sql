ALTER TABLE citations
    ADD COLUMN IF NOT EXISTS source_locator TEXT NULL;

ALTER TABLE citations
    ADD COLUMN IF NOT EXISTS source_breadcrumb TEXT NULL;

ALTER TABLE citations
    ADD COLUMN IF NOT EXISTS source_locator_kind VARCHAR(32) NULL;

ALTER TABLE citations
    DROP CONSTRAINT IF EXISTS citations_source_locator_kind_check;

-- Los valores vienen de locator.py. 'markdown_heading' no es una ubicacion juridica:
-- se guarda para no perder el dato, pero la interfaz no lo muestra.
ALTER TABLE citations
    ADD CONSTRAINT citations_source_locator_kind_check
    CHECK (source_locator_kind IS NULL OR source_locator_kind IN
        ('exact', 'prefix', 'fuzzy', 'markdown_heading', 'snippet_regex'));
