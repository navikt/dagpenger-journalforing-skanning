package no.nav.dagpenger.journalføring.skanning

import mu.KotlinLogging
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.isEttersending
import no.nav.dagpenger.events.isSoknad
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import no.nav.dagpenger.streams.consumeTopic
import no.nav.dagpenger.streams.kbranch
import no.nav.dagpenger.streams.streamConfig
import no.nav.dagpenger.streams.toTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
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

        val søknaderOgEttersendingStreams = inngåendeJournalposter
            .kbranch({ _, behov -> behov.isSoknad() }, { _, behov -> behov.isEttersending() })

        val søknadsStream = søknaderOgEttersendingStreams[0]
            .filterNot { _, behov -> behov.hasSøknadRettighetsType() && behov.hasSøknadVedtakType() }
            .peek { key, _ -> LOGGER.info("Processing behov with HenvendelsesType Søknad with key $key") }
            .mapValues(this::setVedtakstypeOgRettighetsTypeSøknad)

        val ettersendingStream = søknaderOgEttersendingStreams[1]
            .filterNot { _, behov -> behov.hasEttersendingRettighetsType() }
            .peek { key, _ -> LOGGER.info("Processing behov with HenvendelsesType Ettersending with key $key") }
            .mapValues(this::setRettighetstypeEttersending)

        søknadsStream
            .merge(ettersendingStream)
            .peek { key, value -> LOGGER.info("Producing ${value.javaClass} with key $key") }
            .toTopic(
                INNGÅENDE_JOURNALPOST, env.schemaRegistryUrl
            )

        return KafkaStreams(builder.build(), this.getConfig())
    }

    override fun getConfig(): Properties {
        val props = streamConfig(
            appId = SERVICE_APP_ID,
            bootStapServerUrl = env.bootstrapServersUrl,
            credential = KafkaCredential(env.username, env.password)
        )
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "latest"
        return props
    }

    private fun setVedtakstypeOgRettighetsTypeSøknad(behov: Behov): Behov {
        val journalpost = behov.getJournalpost()
        //Handle multiple dokuments
        val navSkjemaId: String? = journalpost.getDokumentListe().first().getNavSkjemaId()

        if (navSkjemaId != null) {
            val vedtakstype = VedtakstypeMapper.mapper.getVedtakstype(navSkjemaId)
            val rettighetstype = RettighetstypeMapper.mapper.getRettighetstype(navSkjemaId)
            behov.getHenvendelsesType().getSøknad().setRettighetsType(rettighetstype)
            behov.getHenvendelsesType().getSøknad().setVedtakstype(vedtakstype)
        }
        return behov
    }

    private fun setRettighetstypeEttersending(behov: Behov): Behov {
        val journalpost = behov.getJournalpost()
        //Handle multiple dokuments
        val navSkjemaId: String? = journalpost.getDokumentListe().first().getNavSkjemaId()

        if (navSkjemaId != null) {
            val rettighetstype = RettighetstypeMapper.mapper.getRettighetstype(navSkjemaId)
            behov.getHenvendelsesType().getEttersending().setRettighetsType(rettighetstype)
        }
        return behov
    }

    private fun Behov.hasSøknadRettighetsType(): Boolean =
        this.getHenvendelsesType().getSøknad().getVedtakstype() != null

    private fun Behov.hasSøknadVedtakType(): Boolean =
        this.getHenvendelsesType().getSøknad().getVedtakstype() != null

    private fun Behov.hasEttersendingRettighetsType(): Boolean =
        this.getHenvendelsesType().getEttersending().getRettighetsType() != null
}
