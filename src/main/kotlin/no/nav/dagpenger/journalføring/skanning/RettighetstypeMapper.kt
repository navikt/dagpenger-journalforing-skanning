package no.nav.dagpenger.journalføring.skanning

import no.nav.dagpenger.events.avro.Rettighetstype

class RettighetstypeMapper {

    object mapper {

        private val typeMap = mapOf(
            "NAV 04-01.03" to Rettighetstype.ORDINÆRE_DAGPENGER,
            "NAV 04-01.04" to Rettighetstype.DAPENGER_UNDER_PERMITERING,
            "NAV 04-16.03" to Rettighetstype.ORDINÆRE_DAGPENGER,
            "NAV 04-16.04" to Rettighetstype.DAPENGER_UNDER_PERMITERING,
            "NAVe 04-01.03" to Rettighetstype.ORDINÆRE_DAGPENGER,
            "NAVe 04-01.04" to Rettighetstype.DAPENGER_UNDER_PERMITERING,
            "NAVe 04-16.03" to Rettighetstype.ORDINÆRE_DAGPENGER,
            "NAVe 04-16.04" to Rettighetstype.DAPENGER_UNDER_PERMITERING
        )

        fun getRettighetstype(navSkjemaId: String): Rettighetstype {
            return typeMap.getOrElse(navSkjemaId) { throw IllegalArgumentException("$navSkjemaId kan ikke mappes") }
        } }
}