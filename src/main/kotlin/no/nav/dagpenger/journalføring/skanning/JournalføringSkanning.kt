package no.nav.dagpenger.journalføring.skanning

import mu.KotlinLogging
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.Dokument
import no.nav.dagpenger.events.avro.JournalpostType
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import no.nav.dagpenger.streams.consumeTopic
import no.nav.dagpenger.streams.toTopic
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder

private val LOGGER = KotlinLogging.logger {}

class JournalføringSkanning(private val journalpostTypeMapping: JournalpostTypeMapping) : Service() {
    override val SERVICE_APP_ID = "journalføring-skanning"

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val service = JournalføringSkanning(JournalpostTypeMappingManual())
            service.start()
        }
    }

    override fun setupStreams(): KafkaStreams {
        println(SERVICE_APP_ID)
        val builder = StreamsBuilder()

        val inngåendeJournalposter = builder.consumeTopic(INNGÅENDE_JOURNALPOST)

        inngåendeJournalposter
                .peek { key, value -> LOGGER.info("Processing ${value.javaClass} with key $key") }
                //.filter(this::containsJsonDokument)
                .filter { _, behov -> behov.getJournalpost().getJournalpostType() == null }
                .mapValues(this::addJournalpostType)
                .peek { key, value -> LOGGER.info("Producing ${value.javaClass} with key $key") }
                .toTopic(INNGÅENDE_JOURNALPOST)

        return KafkaStreams(builder.build(), this.getConfig())
    }

    private fun addJournalpostType(behov: Behov): Behov {
        val journalpost = behov.getJournalpost()
        journalpost.setJournalpostType(JournalpostType.UKJENT)
        return behov
    }

    private fun containsJsonDokument(key: String, behov: Behov): Boolean {
        val isJson: (Dokument) -> Boolean = { false }
        return behov.getJournalpost().getDokumentListe().any(isJson)
    }
}
