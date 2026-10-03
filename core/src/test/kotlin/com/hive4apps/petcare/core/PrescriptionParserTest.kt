package com.hive4apps.petcare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrescriptionParserTest {

    @Test
    fun brazilianPrescription() {
        val text = """
            Clínica Veterinária Bicho Feliz
            Paciente: Thor   Espécie: Canina
            Tutor: Ana Souza
            Uso oral
            1) Apoquel 16 mg ............ 1 caixa
            Dar 1/2 comprimido, via oral, a cada 12 horas, durante 14 dias.
            2) Prednisolona 20mg ......... 1 caixa
            Administrar 1 comprimido 1x ao dia pela manhã por 5 dias
            Uso tópico
            3) Shampoo Cloresten ......... 1 frasco
            Dar banho a cada 3 dias durante 4 semanas, deixar agir 10 minutos.
            4) Otosyn ...... 1 frasco
            Pingar 5 gotas em cada ouvido 12/12h por 10 dias
            Dra. Maria Silva CRMV-RJ 12345
            Retorno em 15 dias
        """.trimIndent()
        val items = PrescriptionParser.parse(text)
        assertEquals(4, items.size, items.joinToString("\n"))

        val apoquel = items[0]
        assertEquals("Apoquel 16 mg", apoquel.name)
        assertEquals(CareKind.MEDICATION, apoquel.kind)
        assertEquals("1/2 comprimido", apoquel.dose)
        assertEquals(2, apoquel.perDay)
        assertEquals(14, apoquel.durationDays)

        val pred = items[1]
        assertEquals("Prednisolona 20mg", pred.name)
        assertEquals(1, pred.perDay)
        assertEquals(5, pred.durationDays)
        assertEquals(TimeHint.MORNING, pred.timeHint)

        val shampoo = items[2]
        assertEquals(CareKind.SHAMPOO, shampoo.kind)
        assertEquals(3, shampoo.everyDays)
        assertEquals(28, shampoo.durationDays)

        val oto = items[3]
        assertEquals(CareKind.EAR_DROPS, oto.kind)
        assertEquals("5 gotas", oto.dose)
        assertEquals(2, oto.perDay)
        assertEquals(10, oto.durationDays)
    }

    @Test
    fun frenchPrescription() {
        val text = """
            Ordonnance
            Nom : Minou  Espèce : féline
            - Metacam 0,5 mg/ml suspension buvable
            3 gouttes par voie orale une fois par jour pendant 7 jours
            - Tobrex collyre
            1 goutte dans chaque oeil 3 fois par jour pendant 10 jours
            - Douxo Calm shampooing
            Laver 2 fois par semaine pendant 1 mois
            - Milbemax chat : 1 comprimé tous les 3 mois
        """.trimIndent()
        val items = PrescriptionParser.parse(text)
        assertEquals(4, items.size, items.joinToString("\n"))

        assertEquals("Metacam 0,5 mg/ml suspension buvable", items[0].name)
        assertEquals("3 gouttes", items[0].dose)
        assertEquals(1, items[0].perDay)
        assertEquals(7, items[0].durationDays)

        assertEquals(CareKind.EYE_DROPS, items[1].kind)
        assertEquals(3, items[1].perDay)
        assertEquals(10, items[1].durationDays)

        assertEquals(CareKind.SHAMPOO, items[2].kind)
        assertEquals(4, items[2].everyDays)
        assertEquals(30, items[2].durationDays)

        assertEquals(CareKind.ANTIPARASITIC, items[3].kind)
        assertEquals("Milbemax chat", items[3].name)
    }

    @Test
    fun vetAbbreviationsAndOneLineItems() {
        val items = PrescriptionParser.parse(
            """
            1. Cefalexina 500mg - 1 comp VO BID x 10 dias
            2. Omeprazol 10mg 1 cp SID em jejum, uso contínuo
            3. Dipirona gotas: 10 gotas de 8/8h se dor
            """.trimIndent(),
        )
        assertEquals(3, items.size, items.joinToString("\n"))
        assertEquals("Cefalexina 500mg", items[0].name)
        assertEquals(2, items[0].perDay)
        assertEquals(10, items[0].durationDays)
        assertEquals(1, items[1].perDay)
        assertTrue(items[1].continuous)
        assertNull(items[1].durationDays)
        assertEquals(3, items[2].perDay)
        assertEquals("10 gotas", items[2].dose)
    }

    @Test
    fun frequencies() {
        fun f(s: String) = PrescriptionParser.frequency(PrescriptionParser.norm(s))
        assertEquals(PrescriptionParser.Frequency(2, 1), f("duas vezes ao dia"))
        assertEquals(PrescriptionParser.Frequency(3, 1), f("de 8 em 8 horas"))
        assertEquals(PrescriptionParser.Frequency(1, 2), f("dias alternados"))
        assertEquals(PrescriptionParser.Frequency(1, 2), f("toutes les 48 h"))
        assertEquals(PrescriptionParser.Frequency(2, 1), f("matin et soir"))
        assertEquals(PrescriptionParser.Frequency(1, 7), f("1x por semana"))
        assertEquals(PrescriptionParser.Frequency(4, 1), f("q6h"))
        assertEquals(PrescriptionParser.Frequency(1, 1), f("une fois par jour"))
    }

    @Test
    fun durations() {
        fun d(s: String) = PrescriptionParser.duration(PrescriptionParser.norm(s))
        assertEquals(7, d("durante sete dias"))
        assertEquals(21, d("por 3 semanas"))
        assertEquals(60, d("pendant 2 mois"))
        assertEquals(10, d("x 10 dias"))
        assertNull(d("retorno em 15 dias"))
    }
}
