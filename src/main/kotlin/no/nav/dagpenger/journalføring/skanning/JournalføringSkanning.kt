package no.nav.dagpenger.journalføring.skanning

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import mu.KotlinLogging
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.Dokument
import no.nav.dagpenger.events.avro.JournalpostType
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import no.nav.dagpenger.streams.configureAvroSerde
import no.nav.dagpenger.streams.consumeTopic
import no.nav.dagpenger.streams.streamConfig
import no.nav.dagpenger.streams.toTopic
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import java.util.Properties

private val LOGGER = KotlinLogging.logger {}

class JournalføringSkanning(val env: Environment, private val journalpostTypeMapping: JournalpostTypeMapping) :
    Service() {
    override val SERVICE_APP_ID =
        "journalføring-skanning" // NB: also used as group.id for the consumer group - do not change!

    override val HTTP_PORT: Int = env.httpPort ?: super.HTTP_PORT

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val service = JournalføringSkanning(Environment(), JournalpostTypeMappingManual())
            service.start()
        }
    }

    override fun setupStreams(): KafkaStreams {
        LOGGER.info { "Initiating start of $SERVICE_APP_ID" }
        val innkommendeJournalpost = INNGÅENDE_JOURNALPOST.copy(
            valueSerde = configureAvroSerde<Behov>(
                mapOf(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to env.schemaRegistryUrl)
            )
        )
        val builder = StreamsBuilder()

        val inngåendeJournalposter = builder.consumeTopic(innkommendeJournalpost)

        inngåendeJournalposter
            .peek { key, value -> LOGGER.info("Processing ${value.javaClass} with key $key") }
            .filter { _, behov -> behov.getJournalpost().getJournalpostType() == null }
            .mapValues(this::addJournalpostType)
            .peek { key, value -> LOGGER.info("Producing ${value.javaClass} with key $key") }
            .toTopic(
                INNGÅENDE_JOURNALPOST.copy(
                    valueSerde = configureAvroSerde<Behov>(
                        mapOf(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to env.schemaRegistryUrl)
                    )
                )
            )

        return KafkaStreams(builder.build(), this.getConfig())
    }

    override fun getConfig(): Properties {
        return streamConfig(
            appId = SERVICE_APP_ID,
            bootStapServerUrl = env.bootstrapServersUrl,
            credential = KafkaCredential(env.username, env.password)
        )
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
