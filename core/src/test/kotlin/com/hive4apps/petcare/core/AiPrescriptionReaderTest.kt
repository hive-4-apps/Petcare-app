package com.hive4apps.petcare.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiPrescriptionReaderTest {
    private val existing = listOf(
        ExistingCare("bath-1", "Shampoo Cloresten", "1 time a day, every 7 days, for 28 days"),
        ExistingCare("lotion-1", "Loção Zelotril", "2 times a day, for 10 days"),
    )

    /** What the API answers for the Vet Life prescription: one printed medicine, two handwritten "Manter" lines. */
    private fun answer(cares: String, stop: String = "end_turn"): String = buildJsonObject {
        put("type", "message")
        put("stop_reason", stop)
        put(
            "content",
            buildJsonArray {
                add(buildJsonObject { put("type", "thinking"); put("thinking", "") })
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", """{"transcription":"1) Amoxilina com clavulanato 500mg\n*Manter banhos 15/15 dias","cares":$cares}""")
                    },
                )
            },
        )
    }.toString()

    private val cares = """[
        {"name":"Amoxicilina com clavulanato 500mg","kind":"MEDICATION","dose":"1 comprimido","perDay":2,"everyDays":1,
         "durationDays":30,"continuous":false,"timeHint":null,"source":"Administrar 1 comprimido a cada 12 horas, por 30 dias.",
         "handwritten":false,"continuesId":null},
        {"name":"Banhos","kind":"SHAMPOO","dose":null,"perDay":null,"everyDays":15,"durationDays":null,"continuous":true,
         "timeHint":null,"source":"*Manter banhos 15/15 dias","handwritten":true,"continuesId":"bath-1"},
        {"name":"Loção nas lesões","kind":"TOPICAL","dose":null,"perDay":2,"everyDays":1,"durationDays":null,"continuous":true,
         "timeHint":null,"source":"*Manter loção nas lesões, 2x ao dia","handwritten":true,"continuesId":"not-an-id"}
    ]"""

    @Test
    fun readsPrintedAndHandwrittenItems() {
        val reading = AiPrescriptionReader.parseResponse(answer(cares), existing)
        assertEquals(3, reading.cares.size)
        assertTrue(reading.transcription.contains("Manter banhos"))

        val amox = reading.cares[0]
        assertEquals(CareKind.MEDICATION, amox.kind)
        assertEquals(2, amox.perDay)
        assertEquals(30, amox.durationDays)
        assertNull(amox.continuesId)

        val baths = reading.cares[1]
        assertEquals(CareKind.SHAMPOO, baths.kind)
        assertEquals(15, baths.everyDays)
        assertEquals(1, baths.perDay, "every n days without perDay = once that day")
        assertTrue(baths.continuous && baths.handwritten)
        assertEquals("bath-1", baths.continuesId)

        // An id the app does not know is dropped: the item becomes a new treatment.
        assertNull(reading.cares[2].continuesId)
    }

    @Test
    fun refusalAndCutAnswersAreErrors() {
        assertFailsWith<AiReadingException> { AiPrescriptionReader.parseResponse(answer("[]", stop = "refusal"), existing) }
        assertFailsWith<AiReadingException> { AiPrescriptionReader.parseResponse(answer("[]", stop = "max_tokens"), existing) }
        assertFailsWith<AiReadingException> { AiPrescriptionReader.parseResponse("not json", existing) }
    }

    @Test
    fun requestSendsEveryPageAndTheExistingTreatments() {
        val request = Json.parseToJsonElement(AiPrescriptionReader.buildRequest(listOf(PageImage("AAA"), PageImage("BBB")), existing)).jsonObject
        assertEquals(AiPrescriptionReader.MODEL, request["model"]!!.jsonPrimitive.content)
        val format = request["output_config"]!!.jsonObject["format"]!!.jsonObject
        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        val content = request["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray.map { it as JsonObject }
        assertEquals(listOf("image", "image", "text"), content.map { it["type"]!!.jsonPrimitive.content })
        assertTrue(content[2]["text"]!!.jsonPrimitive.content.contains("bath-1: Shampoo Cloresten"))
    }
}
