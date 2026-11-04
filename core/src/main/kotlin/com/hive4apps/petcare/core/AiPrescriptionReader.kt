package com.hive4apps.petcare.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

/** A photo of a page, already resized and encoded (JPEG, base64). */
class PageImage(val base64Jpeg: String)

/** A treatment of the pet already followed in the app: the prescription may say to keep it going ("Manter..."). */
data class ExistingCare(val id: String, val name: String, val description: String)

/** What the AI read: the text of the pages (handwriting included) and each care found. */
data class AiReading(val transcription: String, val cares: List<ParsedCare>)

class AiReadingException(message: String) : Exception(message)

/** Which AI reads, told by the key itself: Anthropic keys start with "sk-ant-", Google AI Studio keys with "AIza". */
enum class AiProvider(val label: String) {
    CLAUDE("Claude"), GEMINI("Gemini");

    companion object {
        fun forKey(key: String): AiProvider = if (key.trim().startsWith("AIza")) GEMINI else CLAUDE
    }
}

/**
 * Reads photos of a vet's prescription with Claude (Anthropic API) or Gemini (Google AI Studio, free tier),
 * printed and **handwritten** text alike,
 * and returns the same [ParsedCare] items as the offline [PrescriptionParser].
 *
 * Plain HTTP + kotlinx.serialization on purpose: the official Java SDK weighs ~40 MB, too much for the APK.
 * The photos are sent to Anthropic or Google only when the user has entered their own API key.
 */
object AiPrescriptionReader {
    const val MODEL = "claude-opus-5-5"
    private const val URL_MESSAGES = "https://api.anthropic.com/v1/messages"
    private val json = Json { ignoreUnknownKeys = true }

    /** Gemini model for free AI Studio keys: the "latest" alias follows Google's current Flash model. */
    const val GEMINI_MODEL = "gemini-flash-latest"
    private const val URL_GEMINI = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

    fun read(apiKey: String, pages: List<PageImage>, existing: List<ExistingCare>, timeoutMs: Int = 180_000): AiReading {
        require(pages.isNotEmpty()) { "no page" }
        val key = apiKey.trim()
        return when (AiProvider.forKey(key)) {
            AiProvider.CLAUDE -> parseResponse(
                post(
                    URL_MESSAGES,
                    mapOf(
                        "x-api-key" to key,
                        "anthropic-version" to "2026-10-21",
                        "anthropic-beta" to "server-side-fallback-2026-07-01",
                    ),
                    buildRequest(pages, existing),
                    timeoutMs,
                ),
                existing,
            )
            AiProvider.GEMINI -> parseGeminiResponse(
                post(URL_GEMINI, mapOf("x-goog-api-key" to key), buildGeminiRequest(pages, existing), timeoutMs),
                existing,
            )
        }
    }

    private fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = timeoutMs
            setRequestProperty("content-type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val answer = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes().decodeToString() }.orEmpty()
            if (code !in 200..299) throw AiReadingException(errorMessage(code, answer))
            return answer
        } finally {
            connection.disconnect()
        }
    }

    // ---- Request ----

    fun buildRequest(pages: List<PageImage>, existing: List<ExistingCare>): String = buildJsonObject {
        put("model", MODEL)
        put("max_tokens", 16000)
        put("fallbacks", "default")
        put("system", SYSTEM)
        putJsonObject("output_config") {
            put("effort", "medium")
            putJsonObject("format") {
                put("type", "json_schema")
                put("schema", SCHEMA)
            }
        }
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        pages.forEach { page ->
                            add(
                                buildJsonObject {
                                    put("type", "image")
                                    putJsonObject("source") {
                                        put("type", "base64")
                                        put("media_type", "image/jpeg")
                                        put("data", page.base64Jpeg)
                                    }
                                },
                            )
                        }
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", userText(pages.size, existing))
                            },
                        )
                    }
                },
            )
        }
    }.toString()

    fun buildGeminiRequest(pages: List<PageImage>, existing: List<ExistingCare>): String = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { add(buildJsonObject { put("text", SYSTEM) }) }
        }
        putJsonArray("contents") {
            add(
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        pages.forEach { page ->
                            add(
                                buildJsonObject {
                                    putJsonObject("inline_data") {
                                        put("mime_type", "image/jpeg")
                                        put("data", page.base64Jpeg)
                                    }
                                },
                            )
                        }
                        add(buildJsonObject { put("text", userText(pages.size, existing)) })
                    }
                },
            )
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            put("responseSchema", GEMINI_SCHEMA)
            put("maxOutputTokens", 16000)
        }
    }.toString()

    /** Same shape as [SCHEMA], in the OpenAPI subset Gemini takes (nullable instead of anyOf). */
    private val GEMINI_SCHEMA: JsonObject by lazy { geminiSchema() }

    private fun geminiSchema(): JsonObject = buildJsonObject {
        fun field(type: String, nullable: Boolean = false, values: List<String>? = null) = buildJsonObject {
            put("type", type)
            if (nullable) put("nullable", true)
            if (values != null) putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } }
        }
        put("type", "OBJECT")
        putJsonArray("required") { add(JsonPrimitive("transcription")); add(JsonPrimitive("cares")) }
        putJsonObject("properties") {
            put("transcription", field("STRING"))
            putJsonObject("cares") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonArray("required") { ITEM_FIELDS.forEach { add(JsonPrimitive(it)) } }
                    putJsonArray("propertyOrdering") { ITEM_FIELDS.forEach { add(JsonPrimitive(it)) } }
                    putJsonObject("properties") {
                        put("name", field("STRING"))
                        put("kind", field("STRING", values = CareKind.entries.map { it.name }))
                        put("dose", field("STRING", nullable = true))
                        put("perDay", field("INTEGER", nullable = true))
                        put("everyDays", field("INTEGER", nullable = true))
                        put("durationDays", field("INTEGER", nullable = true))
                        put("continuous", field("BOOLEAN"))
                        put("timeHint", field("STRING", nullable = true, values = TimeHint.entries.map { it.name }))
                        put("source", field("STRING"))
                        put("handwritten", field("BOOLEAN"))
                        put("continuesId", field("STRING", nullable = true))
                    }
                }
            }
        }
    }

    private fun userText(pageCount: Int, existing: List<ExistingCare>): String = buildString {
        append(if (pageCount == 1) "Here is the photo of the vet's document." else "Here are the $pageCount pages of the vet's document, in order.")
        append("\n\n")
        if (existing.isEmpty()) {
            append("The pet has no treatment followed in the app yet: continuesId is always null.")
        } else {
            append("Treatments of this pet already followed in the app (id: name, current schedule):\n")
            existing.forEach { append("- ${it.id}: ${it.name} (${it.description})\n") }
        }
    }

    private val SYSTEM = """
        You read veterinary prescriptions (French or Brazilian Portuguese) photographed with a phone, for an app that
        turns them into a care schedule for the pet's owner. Read the printed text AND every handwritten note: vets
        often add treatments by hand (in the margins, under the signature, between lines). Handwriting matters as much
        as print. Read it carefully, using the context of veterinary care to decide between possible readings.

        For each thing to give or do to the pet (medicine, shampoo/bath, eye or ear drops, lotion/ointment,
        antiparasitic, injection...), return one item:
        - name: the product as written (with its strength, e.g. "Amoxicilina com clavulanato 500mg"). For a handwritten
          line without a product name, a short description in the document's language ("Banhos", "Loção nas lesões").
        - kind: MEDICATION (oral: tablets, capsules, syrup), SHAMPOO (baths, shampoo), EYE_DROPS, EAR_DROPS,
          TOPICAL (lotion, ointment, cream, spray on the skin), INJECTION, ANTIPARASITIC (pipette, flea/tick), OTHER.
        - dose: as written ("1 comprimido", "1/2 comprimé", "3 gotas", "5 ml"), null when not written.
        - perDay: doses per day ("a cada 12 horas" / "12/12h" / "2x ao dia" = 2, "toutes les 8 h" = 3), null if not daily or not written.
        - everyDays: 1 for daily; n for every n days ("15/15 dias" or "a cada 15 dias" = 15, "un jour sur deux" = 2,
          "1x por semana" = 7); null when not written. With everyDays > 1 and no perDay, perDay is 1.
        - durationDays: total days ("por 30 dias" = 30, "pendant 2 semaines" = 14), null when not written.
        - continuous: true for "uso contínuo", "até novas orientações", "en continu", and for an instruction to keep or
          continue a treatment ("Manter...", "Continuar...", "Maintenir...", "Continuer...") that gives no duration.
        - timeHint: MORNING, NOON or EVENING when a single daily dose has a time of day, else null.
        - source: the lines of the document this item comes from, transcribed exactly.
        - handwritten: true when the item (or its instructions) is handwritten.
        - continuesId: when the item keeps going one of the pet's treatments already followed in the app (listed in the
          user message; typically "Manter..." / "Maintenir..."), the id of that treatment; null otherwise or if unsure.

        Ignore the clinic, vet, owner and pet details, quantities to buy ("3cx", "1 frasco"), pharmacy boxes,
        signatures and stamps: they are not things to give. Never invent a value that is not on the document:
        use null. transcription: the whole text of the document in reading order, handwriting included.
    """.trimIndent()

    private fun nullable(type: String): JsonObject = buildJsonObject {
        putJsonArray("anyOf") {
            add(buildJsonObject { put("type", type) })
            add(buildJsonObject { put("type", "null") })
        }
    }

    private fun nullableEnum(values: List<String>): JsonObject = buildJsonObject {
        putJsonArray("anyOf") {
            add(buildJsonObject { put("type", "string"); putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } } })
            add(buildJsonObject { put("type", "null") })
        }
    }

    private val ITEM_FIELDS = listOf(
        "name", "kind", "dose", "perDay", "everyDays", "durationDays", "continuous", "timeHint", "source", "handwritten", "continuesId",
    )

    private val SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") { add(JsonPrimitive("transcription")); add(JsonPrimitive("cares")) }
        putJsonObject("properties") {
            putJsonObject("transcription") { put("type", "string") }
            putJsonObject("cares") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    put("additionalProperties", false)
                    putJsonArray("required") { ITEM_FIELDS.forEach { add(JsonPrimitive(it)) } }
                    putJsonObject("properties") {
                        putJsonObject("name") { put("type", "string") }
                        putJsonObject("kind") {
                            put("type", "string")
                            putJsonArray("enum") { CareKind.entries.forEach { add(JsonPrimitive(it.name)) } }
                        }
                        put("dose", nullable("string"))
                        put("perDay", nullable("integer"))
                        put("everyDays", nullable("integer"))
                        put("durationDays", nullable("integer"))
                        putJsonObject("continuous") { put("type", "boolean") }
                        put("timeHint", nullableEnum(TimeHint.entries.map { it.name }))
                        putJsonObject("source") { put("type", "string") }
                        putJsonObject("handwritten") { put("type", "boolean") }
                        put("continuesId", nullable("string"))
                    }
                }
            }
        }
    }

    // ---- Response ----

    fun parseResponse(body: String, existing: List<ExistingCare>): AiReading {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw AiReadingException("unexpected answer")
        when (root["stop_reason"]?.jsonPrimitive?.contentOrNull) {
            "refusal" -> throw AiReadingException("the AI declined to read this document")
            "max_tokens" -> throw AiReadingException("document too long for the AI")
        }
        val text = root["content"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
            .joinToString("") { it["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
        return parseResult(text, existing)
    }

    fun parseGeminiResponse(body: String, existing: List<ExistingCare>): AiReading {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw AiReadingException("unexpected answer")
        root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull?.let {
            throw AiReadingException("the AI declined to read this document ($it)")
        }
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw AiReadingException("unexpected answer")
        when (val reason = candidate["finishReason"]?.jsonPrimitive?.contentOrNull) {
            null, "STOP" -> Unit
            "MAX_TOKENS" -> throw AiReadingException("document too long for the AI")
            else -> throw AiReadingException("the AI stopped reading ($reason)")
        }
        val text = candidate["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("")
        return parseResult(text, existing)
    }

    private fun parseResult(text: String, existing: List<ExistingCare>): AiReading {
        val result = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw AiReadingException("unexpected answer")
        val ids = existing.map { it.id }.toSet()
        val cares = result["cares"]?.jsonArray.orEmpty().mapNotNull { item -> toCare(item.jsonObject, ids) }
        return AiReading(result.string("transcription").orEmpty(), cares)
    }

    private fun toCare(o: JsonObject, existingIds: Set<String>): ParsedCare? {
        val name = o.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val everyDays = o.int("everyDays")?.takeIf { it >= 1 }
        val perDay = o.int("perDay")?.takeIf { it in 1..24 } ?: if (everyDays != null && everyDays > 1) 1 else null
        return ParsedCare(
            name = name,
            kind = o.string("kind")?.let { k -> CareKind.entries.find { it.name == k } } ?: CareKind.OTHER,
            dose = o.string("dose")?.trim()?.takeIf { it.isNotEmpty() },
            perDay = perDay,
            everyDays = everyDays,
            durationDays = o.int("durationDays")?.takeIf { it >= 1 },
            continuous = o.bool("continuous") == true,
            timeHint = o.string("timeHint")?.let { h -> TimeHint.entries.find { it.name == h } },
            source = o.string("source").orEmpty(),
            handwritten = o.bool("handwritten") == true,
            continuesId = o.string("continuesId")?.takeIf { it in existingIds },
        )
    }

    private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
    private fun JsonObject.string(key: String): String? = prim(key)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = prim(key)?.intOrNull
    private fun JsonObject.bool(key: String): Boolean? = prim(key)?.booleanOrNull

    private fun errorMessage(code: Int, body: String): String {
        val message = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when (code) {
            401, 403 -> "API key refused ($code)"
            429 -> "too many requests, free quota or credit used up (429)"
            else -> "HTTP $code" + (message?.let { ": $it" } ?: "")
        }
    }
}
