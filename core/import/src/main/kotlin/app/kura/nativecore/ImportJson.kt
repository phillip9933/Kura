package app.kura.nativecore

import org.json.JSONArray
import org.json.JSONObject

/** Check the container stack before handing external text to the recursive platform parser. */
internal object ImportJson {
    private const val MAX_DEPTH = 64

    fun objectFrom(text: String) = JSONObject(checked(text))
    fun arrayFrom(text: String) = JSONArray(checked(text))

    fun checked(text: String): String {
        val stack = CharArray(MAX_DEPTH)
        var depth = 0
        var quoted = false
        var escaped = false
        var literal = false
        for (c in text) {
            if (quoted) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> quoted = false
                    else -> require(c >= ' ') { "Invalid JSON string" }
                }
                continue
            }
            when (c) {
                '{', '[' -> {
                    require(depth < MAX_DEPTH) { "JSON nesting exceeds $MAX_DEPTH levels" }
                    stack[depth++] = if (c == '{') '}' else ']'
                }
                '}', ']' -> require(depth > 0 && stack[--depth] == c) { "Invalid JSON container" }
                '"' -> {
                    require(!literal) { "Invalid JSON string boundary" }
                    quoted = true
                }
                ',', ':', ' ', '\r', '\n', '\t' -> Unit
                // Reject comments, alternate quotes and other lenient platform extensions.
                in 'a'..'z', in 'A'..'Z', in '0'..'9', '+', '-', '.' -> {
                    literal = true
                    continue
                }
                else -> throw IllegalArgumentException("Invalid JSON token")
            }
            literal = false
        }
        require(!quoted && depth == 0) { "Incomplete JSON" }
        return text
    }
}
