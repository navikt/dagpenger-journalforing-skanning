package no.nav.dagpenger.journalføring.skanning

import no.nav.dagpenger.events.avro.Rettighetstype
import org.junit.Test
import kotlin.test.assertEquals

class RettighetstypeMapperTest {

    val mapper = RettighetstypeMapper()

    @Test
    fun ` test mapping av henvendelse til rettighetstype Perm`() {
        assertEquals(
            Rettighetstype.DAPENGER_UNDER_PERMITERING,
            RettighetstypeMapper.mapper.getRettighetstype("NAV 04-01.04")
        )
        assertEquals(
            Rettighetstype.DAPENGER_UNDER_PERMITERING,
            RettighetstypeMapper.mapper.getRettighetstype("NAV 04-16.04")
        )
        assertEquals(
            Rettighetstype.DAPENGER_UNDER_PERMITERING,
            RettighetstypeMapper.mapper.getRettighetstype("NAVe 04-01.04")
        )
        assertEquals(
            Rettighetstype.DAPENGER_UNDER_PERMITERING,
            RettighetstypeMapper.mapper.getRettighetstype("NAVe 04-16.04")
        )
    }

    @Test
    fun ` test mapping av henvendelse til rettighetstype ikke Perm`() {
        assertEquals(Rettighetstype.ORDINÆRE_DAGPENGER, RettighetstypeMapper.mapper.getRettighetstype("NAV 04-01.03"))
        assertEquals(Rettighetstype.ORDINÆRE_DAGPENGER, RettighetstypeMapper.mapper.getRettighetstype("NAV 04-16.03"))
        assertEquals(Rettighetstype.ORDINÆRE_DAGPENGER, RettighetstypeMapper.mapper.getRettighetstype("NAVe 04-01.03"))
        assertEquals(Rettighetstype.ORDINÆRE_DAGPENGER, RettighetstypeMapper.mapper.getRettighetstype("NAVe 04-16.03"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun ` test mapping av henvendelse som gir rettighetstype pga 0`() {
        RettighetstypeMapper.mapper.getRettighetstype("0")
    }

    @Test(expected = IllegalArgumentException::class)
    fun ` test mapping av henvendelse som ikke gir rettighetstype`() {
        RettighetstypeMapper.mapper.getRettighetstype("null")
    }

    @Test(expected = IllegalArgumentException::class)
    fun ` test mapping av henvendelse med skjemaId som ikke gir rettighetstype `() {
        RettighetstypeMapper.mapper.getRettighetstype("NAVe 04-06.08")
    }
}