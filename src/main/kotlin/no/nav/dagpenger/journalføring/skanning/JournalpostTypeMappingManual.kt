package no.nav.dagpenger.journalføring.skanning

import no.nav.dagpenger.events.avro.JournalpostType

class JournalpostTypeMappingManual : JournalpostTypeMapping {

    private val typeMap = mapOf(
            "NAV 04-01.030" to JournalpostType.NY,
            "NAV 04-01.04" to JournalpostType.NY,
            "NAV 04-16.03" to JournalpostType.GJENOPPTAK,
            "NAV 04-16.04" to JournalpostType.GJENOPPTAK,
            "NAVe 04-01.03" to JournalpostType.ETTERSENDING,
            "NAVe 04-01.04" to JournalpostType.ETTERSENDING,
            "NAVe 04-16.03" to JournalpostType.ETTERSENDING,
            "NAVe 04-16.04" to JournalpostType.ETTERSENDING
    )

    override fun getJournalpostType(dokumentId: String): JournalpostType {
        return typeMap.getOrDefault(dokumentId, JournalpostType.UKJENT)
    }
}