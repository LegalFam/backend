package com.legalfam.backend.chat.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class N8nAnswerStreamFilterTest {

    @Test
    void passesTextThrough() {
        N8nAnswerStreamFilter filter = new N8nAnswerStreamFilter();
        assertEquals("Hola", filter.append("Hola"));
        assertEquals("Hola mundo", filter.append(" mundo"));
    }

    @Test
    void holdsBackWhatCouldBeTheStartOfTheDelimiter() {
        N8nAnswerStreamFilter filter = new N8nAnswerStreamFilter();
        assertEquals("Fin.", filter.append("Fin.\n<<<LEG"));
        assertNull(filter.append("ALFAM_META>>>"));
        assertNull(filter.append("{\"citations\":[]}"));
        assertEquals("Fin.", filter.lastVisible());
    }

    @Test
    void releasesHeldTextWhenItTurnsOutNotToBeTheDelimiter() {
        N8nAnswerStreamFilter filter = new N8nAnswerStreamFilter();
        assertEquals("a", filter.append("a <<"));
        assertEquals("a << b", filter.append(" b"));
    }

    @Test
    void dropsTrailingWhitespaceBeforeTheDelimiter() {
        N8nAnswerStreamFilter filter = new N8nAnswerStreamFilter();
        assertEquals("Respuesta", filter.append("Respuesta\n\n"));
        assertNull(filter.append("<<<LEGALFAM_META>>>\n{}"));
        assertEquals("Respuesta", filter.lastVisible());
    }

    @Test
    void nothingAfterTheDelimiterIsEverShown() {
        N8nAnswerStreamFilter filter = new N8nAnswerStreamFilter();
        filter.append("Texto <<<LEGALFAM_META>>>");
        assertNull(filter.append(" mas texto"));
        assertEquals("Texto", filter.lastVisible());
    }

    @Test
    void resetStartsOver() {
        N8nAnswerStreamFilter filter = new N8nAnswerStreamFilter();
        filter.append("Primer intento <<<LEGALFAM_META>>>");
        filter.reset();
        assertEquals("", filter.lastVisible());
        assertEquals("Otro", filter.append("Otro"));
    }

    @Test
    void recognizesADelimiterTheModelDecorated() {
        N8nAnswerStreamFilter filter = new N8nAnswerStreamFilter();
        assertEquals("copropiedad.", filter.append("copropiedad.\n\n***LEGAL"));
        assertNull(filter.append("FAM_META***\n{\"citations\": []}"));
        assertEquals("copropiedad.", filter.lastVisible());
    }

    @Test
    void recognizesADelimiterOnTheSameLine() {
        N8nAnswerStreamFilter filter = new N8nAnswerStreamFilter();
        assertEquals("Fin.", filter.append("Fin. **<<<LEGALFAM_META>>>** {}"));
    }

    @Test
    void cutsAtTheFinalJsonWhenTheDelimiterIsMissing() {
        N8nAnswerStreamFilter filter = new N8nAnswerStreamFilter();
        assertEquals("Texto final.", filter.append("Texto final.\n\n```json\n{\n  \"cit"));
        assertNull(filter.append("ations\": []}"));
        assertEquals("Texto final.", filter.lastVisible());
    }

    @Test
    void ordinaryMarkdownIsNotHeldBack() {
        N8nAnswerStreamFilter filter = new N8nAnswerStreamFilter();
        assertEquals("**Bienes propios:** La ley", filter.append("**Bienes propios:** La ley"));
        assertEquals("**Bienes propios:** La ley dice {algo}", filter.append(" dice {algo}"));
    }

    @Test
    void onlyAWordThatCouldBecomeTheDelimiterIsHeld() {
        N8nAnswerStreamFilter filter = new N8nAnswerStreamFilter();
        assertEquals("Ver", filter.append("Ver LEGA"));
        assertEquals("Ver LEGAL y mas", filter.append("L y mas"));
    }
}
