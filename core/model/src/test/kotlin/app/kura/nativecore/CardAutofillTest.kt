package app.kura.nativecore

import org.junit.Assert.*
import org.junit.Test

class CardAutofillTest {
    @Test fun identifiersStayStringsAndNoFieldsAreInvented() {
        val fields = listOf(CardField("member", "Member number"))
        val result = CardAutofill.suggest("Member ID: 000123O8\nSecret: 1234", fields)
        assertEquals(listOf(CardSuggestion(fields.single(), "000123O8")), result)
    }
    @Test fun conflictingValuesAreNotSelected() {
        assertTrue(CardAutofill.suggest("Member ID: 0012\nMember number: 0013", listOf(CardField("n", "Member number"))).isEmpty())
    }
    @Test fun existingTextIsNeverOverwritten() {
        assertTrue(CardAutofill.suggest("Member ID: 0012", listOf(CardField("n", "Member number", "My edit"))).isEmpty())
    }
    @Test fun japaneseLabelsAndOnlyExplicitlyLabelledValues() {
        val fields = listOf(CardField("n", "Member number"), CardField("name", "Member name"))
        val result = CardAutofill.suggest("会員番号：0000123\n氏名: テスト 太郎\nUnlabelled company", fields)
        assertEquals(listOf("0000123", "テスト 太郎"), result.map { it.value })
    }
    @Test fun lateResultsCannotCreateOrReplaceFields() {
        val results = listOf(CardSuggestion(CardField("a", "Member number"), "0012"), CardSuggestion(CardField("unknown", "Other"), "value"))
        assertTrue(CardAutofill.applicable(results, mapOf("a" to "Edited")).isEmpty())
        assertEquals(mapOf("a" to "0012"), CardAutofill.applicable(results, mapOf("a" to "")))
    }
    @Test fun noUnlabelledNumberOrPartialLabelInference() {
        assertTrue(CardAutofill.suggest("000123\nNot Member ID: 8765", listOf(CardField("n", "Member number"))).isEmpty())
    }
    @Test fun japaneseOcrLabelSpacingDoesNotChangeValues() {
        val fields = listOf(CardField("n", "Member number"), CardField("expiry", "Expiry date"), CardField("name", "Member name"))
        val result = CardAutofill.suggest("会 員 番号 : 00001234\n有効 期限 : 2030-12-31\n氏名 : テ スト 太郎", fields)
        assertEquals(listOf("00001234", "2030-12-31", "テ スト 太郎"), result.map { it.value })
    }

}
