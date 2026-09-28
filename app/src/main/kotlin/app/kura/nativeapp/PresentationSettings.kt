package app.kura.nativeapp

import org.json.JSONObject

data class PresentationSettings(val theme: Int = 2, val initialTable: String = "passes",
    val visibleTables: Set<String> = setOf("wallets","passes","identities"),
    val preferences: app.kura.feature.VaultPreferences = app.kura.feature.VaultPreferences()) {
    companion object {
        fun parse(json: JSONObject): PresentationSettings {
            val theme = when(val value = json.opt("themePreference")) {
                is Number -> value.toInt().takeIf { it in 0..2 } ?: 2
                "light" -> 0; "dark" -> 1; else -> 2
            }
            val tables = listOf("wallets" to "showPaymentsTab", "passes" to "showPassesTab", "identities" to "showIdentityTab")
                .filter { json.optBoolean(it.second, true) }.map { it.first }.toSet().ifEmpty { setOf("passes") }
            val requested = listOf("wallets","passes","identities").getOrNull(json.optInt("defaultScreenIndex",1)) ?: "passes"
            return PresentationSettings(theme, requested.takeIf { it in tables } ?: if("passes" in tables) "passes" else tables.first(), tables, app.kura.feature.VaultPreferences(json.toString()))
        }
    }
}