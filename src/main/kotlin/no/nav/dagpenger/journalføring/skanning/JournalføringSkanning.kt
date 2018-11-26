package no.nav.dagpenger.journalføring.skanning

import mu.KotlinLogging
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.Dokument
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import no.nav.dagpenger.streams.consumeTopic
import no.nav.dagpenger.streams.streamConfig
import no.nav.dagpenger.streams.toTopic
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import java.util.Properties

private val LOGGER = KotlinLogging.logger {}

class JournalføringSkanning(val env: Environment) :
    Service() {
    override val SERVICE_APP_ID =
        "journalføring-skanning" // NB: also used as group.id for the consumer group - do not change!

    override val HTTP_PORT: Int = env.httpPort ?: super.HTTP_PORT

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val service = JournalføringSkanning(Environment())
            service.start()
        }
    }

    override fun setupStreams(): KafkaStreams {
        LOGGER.info { "Initiating start of $SERVICE_APP_ID" }

        val builder = StreamsBuilder()
        val inngåendeJournalposter = builder.consumeTopic(INNGÅENDE_JOURNALPOST, env.schemaRegistryUrl)

        inngåendeJournalposter
            .peek { key, value -> LOGGER.info("Processing ${value.javaClass} with key $key") }
            .filter { _, behov -> behov.getHenvendelsesType().getSøknad() != null || behov.getHenvendelsesType().getEttersending() != null }
            //.filter { _, behov -> behov.getHenvendelsesType().getSøknad().getVedtakstype() == null   }
            //.filter { _, behov -> behov.getHenvendelsesType().getSøknad().getRettighetsType() == null   }
            .mapValues(this::setVedtakstype)
            .mapValues(this::setRettighetstype)
            .peek { key, value -> LOGGER.info("Producing ${value.javaClass} with key $key") }
            .toTopic(INNGÅENDE_JOURNALPOST, env.schemaRegistryUrl)

        return KafkaStreams(builder.build(), this.getConfig())
    }

    override fun getConfig(): Properties {
        return streamConfig(
            appId = SERVICE_APP_ID,
            bootStapServerUrl = env.bootstrapServersUrl,
            credential = KafkaCredential(env.username, env.password)
        )
    }

    private fun setVedtakstype(behov: Behov): Behov {
        val journalpost = behov.getJournalpost()

        //Handle multiple dokuments
        val navSkjemaId: String? = journalpost.getDokumentListe().first().getNavSkjemaId()

        if (behov.getHenvendelsesType().getSøknad() != null && navSkjemaId != null) {
            val vedtakstype = VedtakstypeMapper.mapper.getVedtakstype(navSkjemaId)
            behov.getHenvendelsesType().getSøknad().setVedtakstype(vedtakstype)
        }

        return behov
    }

    private fun setRettighetstype(behov: Behov): Behov {
        val journalpost = behov.getJournalpost()

        //Handle multiple dokuments
        val navSkjemaId: String? = journalpost.getDokumentListe().first().getNavSkjemaId()

        /*val rettighetstype = when {
            behov.getMottaker().getIdentifikator() == null -> ""
            else -> RettighetstypeMapper.mapper.getRettighetstype(dokumentId)
        }*/

        if (behov.getHenvendelsesType().getSøknad() != null && navSkjemaId != null) {
            val rettighetstype = RettighetstypeMapper.mapper.getRettighetstype(navSkjemaId)
            behov.getHenvendelsesType().getSøknad().setRettighetsType(rettighetstype)
        }

        if (behov.getHenvendelsesType().getEttersending() != null && navSkjemaId != null) {
            val rettighetstype = RettighetstypeMapper.mapper.getRettighetstype(navSkjemaId)
            behov.getHenvendelsesType().getEttersending().setRettighetsType(rettighetstype)
        }

        return behov
    }

    private fun containsJsonDokument(key: String, behov: Behov): Boolean {
        val isJson: (Dokument) -> Boolean = { false }
        return behov.getJournalpost().getDokumentListe().any(isJson)
    }
}
