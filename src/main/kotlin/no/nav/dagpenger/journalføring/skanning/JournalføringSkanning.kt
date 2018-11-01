package no.nav.dagpenger.journalføring.skanning

import mu.KotlinLogging
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.Dokument
import no.nav.dagpenger.events.avro.JournalpostType
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import no.nav.dagpenger.streams.consumeTopic
import no.nav.dagpenger.streams.streamConfig
import no.nav.dagpenger.streams.toTopic
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import java.util.Properties

private val LOGGER = KotlinLogging.logger {}

private val username: String? = System.getenv("SRVDAGPENGER_JOURNALFORING_SKANNING_USERNAME")
private val password: String? = System.getenv("SRVDAGPENGER_JOURNALFORING_SKANNING_PASSWORD")

class JournalføringSkanning(private val journalpostTypeMapping: JournalpostTypeMapping) : Service() {
    override val SERVICE_APP_ID = "journalføring-skanning"
    override val HTTP_PORT: Int = 8084

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

    override fun getConfig(): Properties {
        return streamConfig(appId = SERVICE_APP_ID, username = username, password = password)
    }

    private fun addJournalpostType(behov: Behov): Behov {
        val journalpost = behov.getJournalpost()

        //Handle multiple dokuments
        val dokumentId = journalpost.getDokumentListe()[0].getDokumentId()

        val journalpostType = when {
            behov.getJournalpost().getSøker().getIdentifikator() == null -> JournalpostType.MANUELL
            else -> journalpostTypeMapping.getJournalpostType(dokumentId)
        }

        behov.getJournalpost().setJournalpostType(journalpostType)

        return behov
    }

    private fun containsJsonDokument(key: String, behov: Behov): Boolean {
        val isJson: (Dokument) -> Boolean = { false }
        return behov.getJournalpost().getDokumentListe().any(isJson)
    }
}
