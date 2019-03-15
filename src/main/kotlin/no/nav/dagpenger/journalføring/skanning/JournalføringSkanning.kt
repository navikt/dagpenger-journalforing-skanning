package no.nav.dagpenger.journalføring.skanning

import mu.KotlinLogging
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.Dokument
import no.nav.dagpenger.events.avro.Ettersending
import no.nav.dagpenger.events.avro.Søknad
import no.nav.dagpenger.events.isEttersending
import no.nav.dagpenger.events.isSoknad
import no.nav.dagpenger.metrics.aCounter
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import no.nav.dagpenger.streams.consumeTopic
import no.nav.dagpenger.streams.kbranch
import no.nav.dagpenger.streams.streamConfig
import no.nav.dagpenger.streams.toTopic
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import java.util.Properties

private val LOGGER = KotlinLogging.logger {}

class JournalføringSkanning(val env: Environment) : Service() {
    override val SERVICE_APP_ID =
        "journalføring-skanning" // NB: also used as group.id for the consumer group - do not change!

    override val HTTP_PORT: Int = env.httpPort ?: super.HTTP_PORT

    private val jpCounter = aCounter(
        name = "journalpost_vedtak_rettighet",
        labelNames = listOf("vedtaksType", "rettighetsType", "containsJsonDocument"),
        help = "Number of Journalposts processed by journalƒøring-skanning"
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val service = JournalføringSkanning(Environment())
            service.start()
        }
    }

    override fun buildTopology(): Topology {
        val builder = StreamsBuilder()
        val inngåendeJournalposter = builder.consumeTopic(INNGÅENDE_JOURNALPOST, env.schemaRegistryUrl)

        val søknaderOgEttersendingStreams = inngåendeJournalposter
            .peek { key, value -> LOGGER.info("Processing ${value.javaClass} with key $key") }
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
            .peek { _, value -> registerMetrics(value) }
            .peek { key, value -> LOGGER.info("Producing ${value.javaClass} with key $key") }
            .toTopic(
                INNGÅENDE_JOURNALPOST, env.schemaRegistryUrl
            )
        return builder.build()
    }

    override fun getConfig(): Properties {
        return streamConfig(
            appId = SERVICE_APP_ID,
            bootStapServerUrl = env.bootstrapServersUrl,
            credential = KafkaCredential(env.username, env.password)
        )
    }

    private fun setVedtakstypeOgRettighetsTypeSøknad(behov: Behov): Behov {
        val journalpost = behov.getJournalpost()
        // Handle multiple dokuments
        val navSkjemaId: String? = journalpost.getDokumentListe().firstOrNull()?.getNavSkjemaId()

        if (navSkjemaId != null) {
            val vedtakstype = VedtakstypeMapper.mapper.getVedtakstype(navSkjemaId)
            val rettighetstype = RettighetstypeMapper.mapper.getRettighetstype(navSkjemaId)

            (behov.getHenvendelsesType() as Søknad).setRettighetsType(rettighetstype)
            (behov.getHenvendelsesType() as Søknad).setVedtakstype(vedtakstype)
        }

        return behov
    }

    private fun setRettighetstypeEttersending(behov: Behov): Behov {
        val journalpost = behov.getJournalpost()
        // Handle multiple dokuments
        val navSkjemaId: String? = journalpost.getDokumentListe().firstOrNull()?.getNavSkjemaId()

        if (navSkjemaId != null) {
            val rettighetstype = RettighetstypeMapper.mapper.getRettighetstype(navSkjemaId)
            (behov.getHenvendelsesType() as Ettersending).setRettighetsType(rettighetstype)
        }
        return behov
    }

    private fun registerMetrics(behov: Behov) {
        val rettighetstype = when {
            behov.hasSøknadRettighetsType() -> (behov.getHenvendelsesType() as Søknad).getRettighetsType().toString()
            behov.hasEttersendingRettighetsType() -> (behov.getHenvendelsesType() as Ettersending).getRettighetsType().toString()
            else -> "unknown"
        }

        val vedtakstype =
            if (behov.hasSøknadVedtakType())
                (behov.getHenvendelsesType() as Søknad).getVedtakstype().toString()
            else "unknown"

        jpCounter.labels(vedtakstype, rettighetstype, containsJsonDokument(behov).toString()).inc()
    }

    private fun containsJsonDokument(behov: Behov): Boolean {
        val isJson: (Dokument) -> Boolean = { false }
        return behov.getJournalpost().getDokumentListe().any(isJson)
    }

    private fun Behov.hasSøknadRettighetsType(): Boolean =
        this.isSoknad() && (this.getHenvendelsesType() as Søknad).getVedtakstype() != null

    private fun Behov.hasSøknadVedtakType(): Boolean =
        this.isSoknad() && (this.getHenvendelsesType() as Søknad).getVedtakstype() != null

    private fun Behov.hasEttersendingRettighetsType(): Boolean =
        this.isEttersending() && (this.getHenvendelsesType() as Ettersending).getRettighetsType() != null
}
