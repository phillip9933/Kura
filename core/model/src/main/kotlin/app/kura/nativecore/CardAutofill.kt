package app.kura.nativecore

/** Host-owned allowlist: recognition may suggest values only for these existing form fields. */
data class CardField(val key: String, val label: String, val current: String = "")
data class CardSuggestion(val field: CardField, val value: String)

object CardAutofill {
    fun suggest(text: String, fields: List<CardField>): List<CardSuggestion> {
        require(text.length <= 64_000 && fields.size <= 128)
        val lines = text.lineSequence().take(256).map(String::trim).filter(String::isNotBlank).toList()
        return fields.distinctBy { it.key }.filter { it.current.isBlank() }.mapNotNull { field ->
            val labels = aliases(field)
            val candidates = lines.mapNotNull { line ->
                labels.firstNotNullOfOrNull { label ->
                    // Japanese OCR can insert spaces inside labels. Match those spaces only;
                    // never normalize or guess characters in the recognized value.
                    val pattern = if (label.any { it.code >= 0x3000 })
                        label.map { Regex.escape(it.toString()) }.joinToString("[\\s\\u3000]*") else Regex.escape(label)
                    Regex("^" + pattern + "\\s*(?:[:：]\\s*|\\s+)(.{1,160})$", RegexOption.IGNORE_CASE)
                        .matchEntire(line)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank)
                }
            }.distinct()
            // Competing identifiers/dates are a manual-entry task; never guess which one wins.
            candidates.singleOrNull()?.let { CardSuggestion(field, it) }
        }
    }

    private fun aliases(field: CardField): List<String> {
        val name = field.label.lowercase()
        val extra = when {
            name in setOf("member number", "borrower number", "card number", "document number") ->
                listOf("Member ID", "Member number", "Membership number", "Card number", "Card No", "会員番号", "カード番号")
            name in setOf("member name", "borrower name", "cardholder", "full name") ->
                listOf("Member name", "Cardholder", "Name", "氏名", "お名前")
            name in setOf("expiry", "expiry date") -> listOf("Expires", "Expiry", "Expiry date", "Valid until", "有効期限")
            name in setOf("issuer", "name / organization", "card name") -> listOf("Issuer", "Organization", "発行元")
            else -> emptyList()
        }
        return (listOf(field.label) + extra).distinct().sortedByDescending(String::length)
    }

    /** Recheck at application time: late OCR must not overwrite edits made while it ran. */
    fun applicable(suggestions: List<CardSuggestion>, current: Map<String, String>): Map<String, String> =
        suggestions.filter { it.field.key in current && current[it.field.key].orEmpty().isBlank() && it.value.isNotBlank() }
            .associate { it.field.key to it.value }
}
