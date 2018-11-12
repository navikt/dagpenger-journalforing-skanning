package no.nav.dagpenger.journalføring.skanning

import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.dagpenger.streams.Topics
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

class JournalføringSkanningComponentTest {

    companion object {
        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"

        val embeddedEnvironment = KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = false,
            withSchemaRegistry = true,
            withSecurity = true,
            topics = listOf(Topics.JOARK_EVENTS.name, Topics.INNGÅENDE_JOURNALPOST.name)
        )

        @BeforeClass
        @JvmStatic
        fun setup() {
            embeddedEnvironment.start()
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            embeddedEnvironment.tearDown()
        }
    }

    @Test
    fun ` embedded kafka cluseter is up and running `() {
        kotlin.test.assertEquals(embeddedEnvironment.serverPark.status, KafkaEnvironment.ServerParkStatus.Started)
    }
}