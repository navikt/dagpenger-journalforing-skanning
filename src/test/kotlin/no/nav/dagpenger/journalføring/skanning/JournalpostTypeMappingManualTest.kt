package no.nav.dagpenger.journalføring.skanning

import no.nav.dagpenger.events.avro.JournalpostType
import org.junit.Test
import kotlin.test.assertEquals

class JournalpostTypeMappingManualTest {

    val mapper = JournalpostTypeMappingManual()

    @Test
    fun ` test mapping av journalpost til type `() {
        assertEquals(JournalpostType.NY, mapper.getJournalpostType("NAV 04-01.03"))
        assertEquals(JournalpostType.UKJENT, mapper.getJournalpostType("0"))
        assertEquals(JournalpostType.UKJENT, mapper.getJournalpostType("null"))
    }

    @Test
    fun ` test mapping av journalpost til type ukjent`() {
        assertEquals(JournalpostType.UKJENT, mapper.getJournalpostType("0"))
        assertEquals(JournalpostType.UKJENT, mapper.getJournalpostType("null"))
    }
}