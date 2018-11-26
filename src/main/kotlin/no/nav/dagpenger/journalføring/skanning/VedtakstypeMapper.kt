package no.nav.dagpenger.journalføring.skanning

import no.nav.dagpenger.events.avro.Annet
import no.nav.dagpenger.events.avro.Ettersending
import no.nav.dagpenger.events.avro.Søknad
import no.nav.dagpenger.events.avro.Vedtakstype
import java.lang.IllegalArgumentException

class VedtakstypeMapper {

    object mapper {

        private val typeMap = mapOf(
            "NAV 04-01.03" to Vedtakstype.NY_RETTIGHET,
            "NAV 04-01.04" to Vedtakstype.NY_RETTIGHET,
            "NAV 04-16.03" to Vedtakstype.GJENOPPTAK,
            "NAV 04-16.04" to Vedtakstype.GJENOPPTAK
        )

        fun getVedtakstype(navSkjemaId: String): Vedtakstype {
            return typeMap.getOrElse(navSkjemaId) {throw IllegalArgumentException("$navSkjemaId kan ikke mappes")
        } }
    }
}