package no.nav.dagpenger.journalføring.skanning

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import mu.KotlinLogging
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.common.embeddedutils.getAvailablePort
import no.nav.dagpenger.events.avro.Annet
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.Dokument
import no.nav.dagpenger.events.avro.Ettersending
import no.nav.dagpenger.events.avro.HenvendelsesType
import no.nav.dagpenger.events.avro.Journalpost
import no.nav.dagpenger.events.avro.Mottaker
import no.nav.dagpenger.events.avro.Søknad
import no.nav.dagpenger.events.isAnnet
import no.nav.dagpenger.events.isEttersending
import no.nav.dagpenger.events.isSoknad
import no.nav.dagpenger.streams.Topics
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.time.Duration
import java.util.Properties
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertEquals

class JournalføringSkanningComponentTest {

    companion object {
        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"

        val embeddedEnvironment = KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = false,
            withSchemaRegistry = true,
            withSecurity = true,
            topics = listOf(Topics.INNGÅENDE_JOURNALPOST.name)
        )

        // given an environment
        val env = Environment(
            username = username,
            password = password,
            bootstrapServersUrl = embeddedEnvironment.brokersURL,
            schemaRegistryUrl = embeddedEnvironment.schemaRegistry!!.url,
            httpPort = getAvailablePort()
        )

        val skanning = JournalføringSkanning(env)

        val behovProducer = behovProducer(env)

        private fun behovProducer(env: Environment): KafkaProducer<String, Behov> {
            val producer: KafkaProducer<String, Behov> = KafkaProducer(Properties().apply {
                put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, env.schemaRegistryUrl)
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, env.bootstrapServersUrl)
                put(ProducerConfig.CLIENT_ID_CONFIG, "dummy-behov-producer-${Random.nextInt()}")
                put(
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                    Topics.INNGÅENDE_JOURNALPOST.keySerde.serializer().javaClass.name
                )
                put(
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                    Topics.INNGÅENDE_JOURNALPOST.valueSerde.serializer().javaClass.name
                )
                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
                put(SaslConfigs.SASL_MECHANISM, "PLAIN")
                put(
                    SaslConfigs.SASL_JAAS_CONFIG,
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.username}\" password=\"${env.password}\";"
                )
            })

            return producer
        }

        @BeforeClass
        @JvmStatic
        fun setup() {
            embeddedEnvironment.start()
            skanning.start()
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            skanning.stop()
            embeddedEnvironment.tearDown()
        }
    }

    @Test
    fun ` embedded kafka cluster is up and running `() {
        kotlin.test.assertEquals(embeddedEnvironment.serverPark.status, KafkaEnvironment.ServerParkStatus.Started)
    }

    @Test
    fun ` skal kunne legge på rettighetstype og vedtakstype for Søknad `() {

        val behovIds = IntRange(1, 10).asSequence().map { it.toString() }.toSet()

        behovIds.forEach { behovId ->
            val innkommendeBehov: Behov = Behov
                .newBuilder()
                .setBehovId(behovId)
                .setMottaker(Mottaker(UUID.randomUUID().toString()))
                .setHenvendelsesType(HenvendelsesType(Søknad(), null, null))
                .setJournalpost(
                    Journalpost
                        .newBuilder()
                        .setJournalpostId(UUID.randomUUID().toString())
                        .setDokumentListe(listOf(Dokument.newBuilder().setDokumentId("123").setNavSkjemaId("NAV 04-01.04").build()))
                        .build()
                )
                .build()
            behovProducer.send(ProducerRecord(INNGÅENDE_JOURNALPOST.name, behovId, innkommendeBehov))
                .get()
        }

        val behovConsumer: KafkaConsumer<String, Behov> = behovConsumer(env, "test-dagpenger-skanning-consumer-1")
        val behovsListe = behovConsumer.poll(Duration.ofSeconds(5)).toList()

        assertEquals(
            10,
            behovsListe
                .filter { behov -> behovIds.contains(behov.value().getBehovId()) }
                .filter { behov -> behov.value().isSoknad() }
                .filter { behov -> behov.value().getHenvendelsesType().getSøknad().getRettighetsType() != null && behov.value().getHenvendelsesType().getSøknad().getVedtakstype() != null }.size
        )
    }

    @Test
    fun ` skal kunne legge på rettighetstype for Ettersending `() {

        val behovIds = IntRange(11, 21).asSequence().map { it.toString() }.toSet()

        behovIds.forEach { behovId ->
            val innkommendeBehov: Behov = Behov
                .newBuilder()
                .setBehovId(behovId)
                .setMottaker(Mottaker(UUID.randomUUID().toString()))
                .setHenvendelsesType(HenvendelsesType(null, Ettersending(), null))
                .setJournalpost(
                    Journalpost
                        .newBuilder()
                        .setJournalpostId(UUID.randomUUID().toString())
                        .setDokumentListe(listOf(Dokument.newBuilder().setDokumentId("123").setNavSkjemaId("NAV 04-01.04").build()))
                        .build()
                )
                .build()

            behovProducer.send(ProducerRecord(INNGÅENDE_JOURNALPOST.name, behovId, innkommendeBehov))
                .get()
        }

        val behovConsumer: KafkaConsumer<String, Behov> = behovConsumer(env, "test-dagpenger-skanning-consumer-2")
        val behovsListe = behovConsumer.poll(Duration.ofSeconds(20)).toList()

        assertEquals(
            11,
            behovsListe
                .filter { behov -> behovIds.contains(behov.value().getBehovId()) }
                .filter { behov -> behov.value().isEttersending() }
                .filter { behov -> behov.value().getHenvendelsesType().getEttersending().getRettighetsType() != null }.size
        )
    }

    @Test
    fun ` skal ikke prossesere Annet `() {

        val behovIds = IntRange(22, 32).asSequence().map { it.toString() }.toSet()

        behovIds.forEach { behovId ->
            val innkommendeBehov: Behov = Behov
                .newBuilder()
                .setBehovId(behovId)
                .setMottaker(Mottaker(UUID.randomUUID().toString()))
                .setHenvendelsesType(HenvendelsesType(null, null, Annet()))
                .setJournalpost(
                    Journalpost
                        .newBuilder()
                        .setJournalpostId(UUID.randomUUID().toString())
                        .setDokumentListe(listOf(Dokument.newBuilder().setDokumentId("123").setNavSkjemaId("NAV 04-01.04").build()))
                        .build()
                )
                .build()

            behovProducer.send(ProducerRecord(INNGÅENDE_JOURNALPOST.name, behovId, innkommendeBehov))
                .get()
        }

        val behovConsumer: KafkaConsumer<String, Behov> = behovConsumer(env, "test-dagpenger-skanning-consumer-3")
        val behovsListe = behovConsumer.poll(Duration.ofSeconds(20)).toList()

        assertEquals(
            11,
            behovsListe
                .filter { behov -> behovIds.contains(behov.value().getBehovId()) }
                .filter { behov -> behov.value().isAnnet() }.size
        )
    }

    private fun behovConsumer(env: Environment, groupId: String): KafkaConsumer<String, Behov> {
        val consumer: KafkaConsumer<String, Behov> = KafkaConsumer(Properties().apply {
            put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, env.schemaRegistryUrl)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, env.bootstrapServersUrl)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                INNGÅENDE_JOURNALPOST.keySerde.deserializer().javaClass.name
            )
            put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                INNGÅENDE_JOURNALPOST.valueSerde.deserializer().javaClass.name
            )
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(
                SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.username}\" password=\"${env.password}\";"
            )
        })

        consumer.subscribe(listOf(INNGÅENDE_JOURNALPOST.name))
        return consumer
    }
}