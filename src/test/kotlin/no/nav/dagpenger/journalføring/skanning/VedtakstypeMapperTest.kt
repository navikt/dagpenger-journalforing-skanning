package no.nav.dagpenger.journalføring.skanning

import no.nav.dagpenger.events.avro.JournalpostType
import no.nav.dagpenger.events.avro.Vedtakstype
import org.junit.Test
import java.lang.IllegalArgumentException
import kotlin.test.assertEquals

class VedtakstypeMapperTest {

    val mapper = VedtakstypeMapper()

    @Test
    fun ` test mapping av henvendelse til vedtakstype Ny rettighet `() {
        assertEquals(Vedtakstype.NY_RETTIGHET, VedtakstypeMapper.mapper.getVedtakstype("NAV 04-01.03"))
        assertEquals(Vedtakstype.NY_RETTIGHET, VedtakstypeMapper.mapper.getVedtakstype("NAV 04-01.04"))
    }

    @Test
    fun ` test mapping av henvendelse til vedtakstype gjenopptak `() {
        assertEquals(Vedtakstype.GJENOPPTAK, VedtakstypeMapper.mapper.getVedtakstype("NAV 04-16.03"))
        assertEquals(Vedtakstype.GJENOPPTAK, VedtakstypeMapper.mapper.getVedtakstype("NAV 04-16.04"))
    }

    @Test (expected = IllegalArgumentException :: class)
    fun ` test mapping av henvendelse som gir vedtakstype pga 0`() {
        VedtakstypeMapper.mapper.getVedtakstype("0")
    }

    @Test (expected = IllegalArgumentException :: class)
    fun ` test mapping av henvendelse som ikke gir vedtakstype`() {
        VedtakstypeMapper.mapper.getVedtakstype("null")
    }

    @Test (expected = IllegalArgumentException :: class)
    fun ` test mapping av henvendelse med skjemaId som ikke gir vedtakstype `() {
        VedtakstypeMapper.mapper.getVedtakstype("NAVe 04-06.08")
    }
}