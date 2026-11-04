package com.hive4apps.petcare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Text of the Vet Life prescription as Google Lens (or a chat app) gives it, pasted in PetCare. */
class MaintainLinesTest {
    private val text = """
        Receituário de Controle Especial
        Nome do animal: Suzi
        Prescrição: USO ORAL:
        1) Amoxilina com clavulanato 500mg--------uso humano---------3cx.
        Administrar 1 comprimido a cada 12 horas, por 30 dias.
        Ana Carolina Huguenin
        10871
        * Manter banhos 15/15 dias.
        * Manter loção nas lesões, 2x ao dia.
        Farmácia veterinária ( ) Farmácia Humana ( )
    """.trimIndent()

    private fun t(id: String, name: String, kind: CareKind, start: String) =
        Treatment(id, "suzi", name, kind, startDate = start)

    @Test
    fun readsPrintedAndKeptCares() {
        val items = PrescriptionParser.parse(text)
        assertEquals(listOf("Amoxilina com clavulanato 500mg", "Banhos", "Loção nas lesões"), items.map { it.name }, items.joinToString("\n"))

        val amox = items[0]
        assertEquals(2, amox.perDay)
        assertEquals(30, amox.durationDays)
        assertFalse(amox.maintain)

        val baths = items[1]
        assertEquals(CareKind.SHAMPOO, baths.kind)
        assertEquals(15, baths.everyDays)
        assertTrue(baths.maintain && baths.continuous)

        val lotion = items[2]
        assertEquals(CareKind.TOPICAL, lotion.kind)
        assertEquals(2, lotion.perDay)
        assertTrue(lotion.maintain && lotion.continuous)
    }

    @Test
    fun linksKeptCaresToTheTreatmentsFollowed() {
        val followed = listOf(
            t("old-bath", "Shampoo Peroxydex", CareKind.SHAMPOO, "2026-08-01"),
            t("bath", "Shampoo Cloresten", CareKind.SHAMPOO, "2026-10-30"),
            t("lotion", "Loção Zelotril", CareKind.TOPICAL, "2026-10-30"),
            t("pill", "Apoquel", CareKind.MEDICATION, "2026-10-30"),
        )
        val items = PrescriptionParser.parse(text)
        assertNull(PrescriptionParser.continuedTreatment(items[0], followed))
        assertEquals("bath", PrescriptionParser.continuedTreatment(items[1], followed)?.id, "latest of the same kind")
        assertEquals("lotion", PrescriptionParser.continuedTreatment(items[2], followed)?.id, "shares a word of its name")
    }
}
