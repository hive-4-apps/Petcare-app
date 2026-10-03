package com.hive4apps.petcare.core

import java.text.Normalizer

/** One medicine (or shampoo, drops...) found in the text of a vet document. Unknown fields stay null. */
data class ParsedCare(
    val name: String,
    val kind: CareKind,
    val dose: String?,
    val perDay: Int?,
    val everyDays: Int?,
    /** Days; null when not written. */
    val durationDays: Int?,
    val continuous: Boolean,
    val timeHint: TimeHint?,
    /** The lines of the document this was read from. */
    val source: String,
)

/**
 * Reads a prescription written in French or Brazilian Portuguese (as the phone's text recognition gives it)
 * and finds each item with its dose, frequency and duration.
 *
 * Prescriptions are lists: an item starts with a number ("1)", "2.", "-") or with the name of a product
 * (a line with mg / ml / comprimido...), followed by the instructions on the next lines.
 */
object PrescriptionParser {

    fun parse(text: String): List<ParsedCare> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val blocks = mutableListOf<MutableList<String>>()
        for (line in lines) {
            val n = norm(line)
            if (isNoise(n)) continue
            val startsItem = ITEM_START.containsMatchIn(line) || looksLikeProductLine(n) && blocks.lastOrNull()
                ?.let { block -> hasInstructions(norm(block.joinToString(" "))) } != false
            when {
                startsItem -> blocks += mutableListOf(line)
                blocks.isNotEmpty() -> blocks.last() += line
                // Instructions before any product line: belongs to nothing, unless it names a product itself.
                hasInstructions(n) || looksLikeProductLine(n) -> blocks += mutableListOf(line)
            }
        }
        return blocks.mapNotNull(::readBlock)
    }

    private fun readBlock(block: List<String>): ParsedCare? {
        val all = block.joinToString(" ")
        val n = norm(all)
        val name = cleanName(block.first()) ?: return null
        val kind = kindOf(n)
        val freq = frequency(n)
        val duration = duration(n)
        val dose = dose(all)
        // A heading or a stray sentence is not a medicine: needs at least one instruction or a product word.
        if (freq == null && duration == null && dose == null && !looksLikeProductLine(norm(block.first())) &&
            kind == CareKind.MEDICATION
        ) {
            return null
        }
        return ParsedCare(
            name = name,
            kind = kind,
            dose = dose,
            perDay = freq?.perDay,
            everyDays = freq?.everyDays,
            durationDays = duration,
            continuous = CONTINUOUS.containsMatchIn(n),
            timeHint = timeHint(n),
            source = block.joinToString("\n"),
        )
    }

    // ---- Text helpers ----

    /** Lower case, no accents, single spaces, "½" -> "1/2": what the patterns match against. */
    fun norm(text: String): String = Normalizer.normalize(text.replace("½", "1/2").replace("¼", "1/4"), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace('’', '\'')
        .replace(Regex("\\s+"), " ")
        .trim()

    private val ITEM_START = Regex("^\\s*(\\d{1,2}\\s*[).:\\-–](?![\\d/])|\\d{1,2}\\s*[ºo°]\\s|[•●▪·*\\-–]\\s)")

    private val PRODUCT_WORDS = Regex(
        "\\b(\\d+[.,]?\\d*\\s?(mg|mcg|µg|ug|g|ml|ui|%)|comprimidos?|comp\\b|cps?\\b|capsulas?|gelules?|comprimes?|" +
            "suspensao|suspension|solucao|solution|xarope|sirop|gotas|gouttes|colirio|collyre|pomada|pommade|creme|" +
            "shampoo|shampooing|xampu|sabonete|spray|pipeta|pipette|injetavel|injectable|ampola|ampoule|" +
            "sache|sachet|frasco|flacon|caixa|boite|bisnaga|tube)\\b",
    )

    private fun looksLikeProductLine(n: String): Boolean = PRODUCT_WORDS.containsMatchIn(n) && !hasInstructions(n)

    private val INSTRUCTION = Regex(
        "\\b(dar|administrar|aplicar|ministrar|oferecer|pingar|instilar|banhar|dar banho|fazer|usar|passar|" +
            "donner|administrer|appliquer|instiller|mettre|faire|utiliser|laver|" +
            "via oral|par voie|vo\\b|sid|bid|tid|qid|q\\d+h|cada|vezes|vez|fois|durante|pendant|por \\d+|toutes|tous|" +
            "ao dia|por dia|par jour|/dia|/jour|horas|heures)",
    )

    private fun hasInstructions(n: String): Boolean = INSTRUCTION.containsMatchIn(n)

    /** Header, footer and identity lines of a prescription. */
    private val NOISE = Regex(
        "^(uso (oral|topico|externo|interno|otologico|oftalmico|veterinario)|usage (oral|externe|interne|local|veterinaire)|" +
            "voie (orale|externe|locale)|receita|receituario|prescricao|ordonnance|prescription|" +
            "(paciente|animal|nome|tutor|proprietario|responsavel|especie|raca|sexo|idade|peso|data|" +
            "patient|nom|proprietaire|espece|race|age|poids|date|adresse|endereco|telefone|tel|cpf|rg)\\s*:|" +
            "crmv|ordre des veterinaires|dr\\.?a?\\s|dra\\s|docteur|assinatura|signature|carimbo|cachet|" +
            "clinica|hospital veterinario|clinique|cabinet|retorno|retour|revisao|proxima consulta|prochain rendez-vous|" +
            "medica? veterinari|veterinaire\\b)",
    )

    private fun isNoise(n: String): Boolean =
        NOISE.containsMatchIn(n) || n.length < 3 || Regex("^[\\d\\s/.:-]+$").matches(n) ||
            Regex("(www\\.|@|https?:)").containsMatchIn(n)

    private fun cleanName(line: String): String? {
        var s = line
            .replace(Regex("^\\s*(\\d{1,2}\\s*[).:\\-–]|\\d{1,2}\\s*[ºo°]\\s|[•●▪·*\\-–]\\s)\\s*"), "")
            // "Apoquel 16 mg ........ 1 caixa", "Otosyn — 1 frasco"
            .replace(Regex("[._…]{2,}.*$"), "")
            .replace(
                Regex(
                    "\\s*[-–—(]?\\s*\\b(\\d+|uma?|une?)\\s+(caixas?|frascos?|bisnagas?|tubos?|cartelas?|boites?|boîtes?|flacons?|tubes?|unidades?|unites?|unités?)\\b.*$",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(Regex("\\b(q\\.?s\\.?p\\.?|uso (oral|topico|tópico|externo)|usage (oral|externe)|voie orale)\\b.*$", RegexOption.IGNORE_CASE), "")
            .trim(' ', '-', '–', ':', ',', ';', '.')
        // Name and instructions on one line: "Apoquel 16mg dar 1/2 comprimido 12/12h" -> keep the name part.
        val n = norm(s)
        val cut = INSTRUCTION.find(n)?.range?.first
        if (cut != null && cut > 2) s = s.take(cut.coerceAtMost(s.length)).trim(' ', '-', '–', ':', ',', ';', '.')
        // "Cefalexina 500mg - 1 comp", "Milbemax chat : 1 comprimé" -> the dose is not part of the name.
        val doseAt = DOSE.find(s)?.range?.first
        if (doseAt != null && doseAt > 2) s = s.take(doseAt).trim(' ', '-', '–', ':', ',', ';', '.')
        if (s.length < 2 || !s.any { it.isLetter() }) return null
        return s.replaceFirstChar { it.uppercase() }
    }

    // ---- Kind ----

    private fun kindOf(n: String): CareKind = when {
        Regex("\\b(shampoo|shampooing|xampu|banho|bain|sabonete|lavar|laver)\\b").containsMatchIn(n) -> CareKind.SHAMPOO
        Regex("\\b(colirio|collyre|oftalm|ophtalm|olhos?|yeux|oeil|ocular|oculaire)").containsMatchIn(n) -> CareKind.EYE_DROPS
        Regex("\\b(otolog|auricular|ouvidos?|oreilles?|otite|conduto)").containsMatchIn(n) -> CareKind.EAR_DROPS
        Regex("\\b(injet|injec|subcutan|sous-cutan|intramuscular|vacina|vaccin)").containsMatchIn(n) -> CareKind.INJECTION
        Regex("\\b(pipeta|pipette|antipulgas|anti-?puces|carrapat|tiques|vermifug|vermifuge|antiparasit|bravecto|nexgard|simparic|frontline|drontal|milbemax)").containsMatchIn(n) ->
            CareKind.ANTIPARASITIC
        Regex("\\b(pomada|pommade|creme|gel|spray|locao|lotion|topico|topique|aplicar na|appliquer sur|ferida|plaie|curativo|pansement)").containsMatchIn(n) ->
            CareKind.TOPICAL
        else -> CareKind.MEDICATION
    }

    // ---- Frequency ----

    data class Frequency(val perDay: Int, val everyDays: Int)

    private val NUMBER_WORDS = mapOf(
        "um" to 1, "uma" to 1, "un" to 1, "une" to 1, "dois" to 2, "duas" to 2, "deux" to 2,
        "tres" to 3, "trois" to 3, "quatro" to 4, "quatre" to 4, "cinco" to 5, "cinq" to 5,
        "seis" to 6, "six" to 6, "sete" to 7, "sept" to 7, "oito" to 8, "huit" to 8, "nove" to 9, "neuf" to 9,
        "dez" to 10, "dix" to 10, "onze" to 11, "doze" to 12, "douze" to 12, "catorze" to 14, "quatorze" to 14,
        "quinze" to 15, "vinte" to 20, "vingt" to 20, "trinta" to 30, "trente" to 30,
    )
    private val NUM = "(\\d+|" + NUMBER_WORDS.keys.sortedByDescending { it.length }.joinToString("|") + ")"

    private fun num(token: String): Int? = token.toIntOrNull() ?: NUMBER_WORDS[token]

    fun frequency(n: String): Frequency? {
        // Vet abbreviations.
        Regex("\\b(sid|bid|tid|qid)\\b").find(n)?.let {
            return Frequency(mapOf("sid" to 1, "bid" to 2, "tid" to 3, "qid" to 4).getValue(it.value), 1)
        }
        // Every N hours: "12/12h", "8/8 horas", "q12h", "a cada 12 horas", "de 12 em 12 horas", "toutes les 12 h".
        val hours = Regex("\\b(\\d{1,2})\\s*/\\s*\\1\\s*h").find(n)?.groupValues?.get(1)
            ?: Regex("\\bq\\s?(\\d{1,2})\\s?h\\b").find(n)?.groupValues?.get(1)
            ?: Regex("\\b(?:a )?cada $NUM\\s*(?:h\\b|hrs?\\b|horas?)").find(n)?.groupValues?.get(1)
            ?: Regex("\\bde $NUM em \\1\\s*(?:h\\b|hrs?\\b|horas?)").find(n)?.groupValues?.get(1)
            ?: Regex("\\b(?:toutes les|tous les|chaque) $NUM\\s*(?:h\\b|heures?)").find(n)?.groupValues?.get(1)
            ?: Regex("\\b(?:every) (\\d{1,2})\\s*h").find(n)?.groupValues?.get(1)
        hours?.let(::num)?.takeIf { it in 1..72 }?.let { h ->
            return if (h <= 24) Frequency((24 / h).coerceAtLeast(1), 1) else Frequency(1, (h + 12) / 24)
        }
        // Every N days.
        Regex("\\b(dias alternados|dia sim,? dia nao|um dia sim|un jour sur deux|tous les deux jours|jours alternes)").find(n)?.let {
            return Frequency(1, 2)
        }
        val days = Regex("\\b(?:a )?cada $NUM\\s*dias?").find(n)?.groupValues?.get(1)
            ?: Regex("\\bde $NUM em \\1\\s*dias").find(n)?.groupValues?.get(1)
            ?: Regex("\\b(?:tous les|toutes les|chaque) $NUM\\s*jours?").find(n)?.groupValues?.get(1)
            ?: Regex("\\b(\\d{1,2})\\s*/\\s*\\1\\s*dias").find(n)?.groupValues?.get(1)
        days?.let(::num)?.takeIf { it in 1..90 }?.let { return Frequency(1, it) }
        // Per week / month.
        Regex("\\b$NUM\\s*(?:x|vezes|vez|fois)\\s*(?:por|na|a|par|/)\\s*(?:semana|semaine)").find(n)?.let {
            val k = num(it.groupValues[1]) ?: 1
            return Frequency(1, Math.round(7.0 / k).toInt().coerceAtLeast(1))
        }
        if (Regex("\\b(semanal|semanalmente|hebdomadaire|par semaine|por semana|a cada semana|toutes les semaines|chaque semaine|1x/semana)").containsMatchIn(n)) {
            return Frequency(1, 7)
        }
        if (Regex("\\b(mensal|mensalmente|por mes|ao mes|par mois|mensuel|tous les mois|chaque mois)").containsMatchIn(n)) {
            return Frequency(1, 30)
        }
        // N times a day: "2x ao dia", "2 vezes ao dia", "3x/dia", "deux fois par jour", "2 fois/j".
        Regex("\\b$NUM\\s*(?:x|vezes|vez|fois)\\s*(?:ao|por|par|/|a|no|le)?\\s*(?:dia|jour|j)\\b").find(n)?.let {
            num(it.groupValues[1])?.takeIf { k -> k in 1..12 }?.let { k -> return Frequency(k, 1) }
        }
        if (Regex("(manha e (a )?noite|matin et soir|manha e tarde|de manha e a noite)").containsMatchIn(n)) {
            return Frequency(2, 1)
        }
        if (Regex("\\b(1x ao dia|uma vez ao dia|uma vez por dia|une fois par jour|diariamente|todos os dias|tous les jours|chaque jour|quotidien|ao dia|por dia|par jour|/dia|/jour)").containsMatchIn(n)) {
            return Frequency(1, 1)
        }
        return null
    }

    // ---- Duration ----

    fun duration(n: String): Int? {
        val unit = "(dias?|jours?|d\\b|j\\b|semanas?|semaines?|meses|mes|mois)"
        val m = Regex("\\b(?:durante|por|pendant|pdt|sur|x|during|for)\\s*(?:mais\\s*)?$NUM\\s*$unit").find(n)
            ?: Regex("\\b$NUM\\s*$unit\\s*(?:de tratamento|de traitement|seguidos|consecutifs|consecutivos)").find(n)
            ?: Regex("\\(\\s*$NUM\\s*$unit\\s*\\)").find(n)
            ?: return null
        val k = num(m.groupValues[1]) ?: return null
        val u = m.groupValues[2]
        val days = when {
            u.startsWith("seman") -> k * 7
            u.startsWith("mes") || u.startsWith("mois") -> k * 30
            else -> k
        }
        return days.takeIf { it in 1..730 }
    }

    private val CONTINUOUS = Regex(
        "\\b(uso continuo|uso contínuo|continuamente|ate novas? (orientac|ordem)|ate segunda ordem|usage continu|" +
            "en continu|jusqu'a nouvel ordre|a vie|pour toujours|indefinidamente)",
    )

    // ---- Dose ----

    private val DOSE = Regex(
        "(?i)(\\d+(?:[.,]\\d+)?(?:\\s*/\\s*\\d+)?|½|¼|meio|meia|uma?|une?|duas|dois|deux|trois|tres|três)\\s*" +
            "(comprimidos?|comprimés?|comprimes?|comp\\.?|cp\\.?|c[aá]psulas?|g[eé]lules?|gotas?|gouttes?|ml|mL|" +
            "pipetas?|pipettes?|sach[eêé]s?|sachets?|doses?|borrifadas?|pulv[eé]risations?|jatos?|aplica[cç][oõ]es?|" +
            "applications?|medidas?|mesures?|tabletes?|pastilhas?)(?!\\p{L})",
    )

    private fun dose(text: String): String? = DOSE.find(text)?.value?.trim()
        ?.replace(Regex("\\s+"), " ")

    private fun timeHint(n: String): TimeHint? = when {
        Regex("\\b(a noite|pela noite|noturno|le soir|au coucher|ao deitar|soir)\\b").containsMatchIn(n) -> TimeHint.EVENING
        Regex("\\b(ao meio-?dia|a midi|midi|almoco|dejeuner)\\b").containsMatchIn(n) -> TimeHint.NOON
        Regex("\\b(pela manha|de manha|le matin|matin|em jejum|a jeun)\\b").containsMatchIn(n) -> TimeHint.MORNING
        else -> null
    }
}
