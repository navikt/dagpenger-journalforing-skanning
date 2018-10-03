package no.nav.dagpenger.journalføring.skanning

import no.nav.dagpenger.events.avro.JournalpostType

interface JournalpostTypeMapping {
    fun getJournalpostType(dokumentId: String): JournalpostType
}