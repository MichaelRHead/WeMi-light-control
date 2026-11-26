package org.suppressingfire.wemi.network

/**
 * Minimal zero-dependency RFC 8259 JSON string escaping and bounded recursive-descent object parser.
 *
 * Enforces explicit nesting depth and string-length safety limits without importing `org.json` or
 * third-party JSON libraries, keeping unit tests and Android runtime behavior identical.
 */
internal object StrictJsonCodec {
    const val MAX_JSON_DEPTH = 12
    const val MAX_JSON_INPUT_CHARS = 512 * 1024
    const val MAX_TOTAL_NODES = 16_384
    private const val MAX_JSON_STRING_CHARS = 512 * 1024

    fun quoteJson(value: String): String = buildString(value.length + 2) {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
        append('"')
    }

    fun parseJsonObject(input: String): Map<String, Any?> {
        if (input.length > MAX_JSON_INPUT_CHARS) {
            throw IllegalArgumentException("JSON payload exceeds maximum size of $MAX_JSON_INPUT_CHARS chars")
        }
        val parser = Parser(input)
        val parsed = parser.parseRootObject()
        parser.expectEnd()
        return parsed
    }

    private class Parser(private val src: String) {
        private var pos = 0
        private var totalNodes = 0

        private fun bumpNodeCount() {
            totalNodes++
            if (totalNodes > MAX_TOTAL_NODES) {
                throw IllegalArgumentException("JSON node count exceeds safety bound $MAX_TOTAL_NODES")
            }
        }

        fun parseRootObject(): Map<String, Any?> {
            skipWhitespace()
            return parseObject(depth = 1)
        }

        fun expectEnd() {
            skipWhitespace()
            if (pos != src.length) {
                throw IllegalArgumentException("Unexpected trailing characters in JSON at offset $pos")
            }
        }

        private fun parseValue(depth: Int): Any? {
            if (depth > MAX_JSON_DEPTH) {
                throw IllegalArgumentException("JSON nesting exceeds maximum depth $MAX_JSON_DEPTH")
            }
            bumpNodeCount()
            skipWhitespace()
            if (pos >= src.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (src[pos]) {
                '{' -> parseObject(depth + 1)
                '[' -> parseArray(depth + 1)
                '"' -> parseString()
                't' -> consumeLiteral("true", true)
                'f' -> consumeLiteral("false", false)
                'n' -> consumeLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected JSON token '${src[pos]}' at offset $pos")
            }
        }

        private fun parseObject(depth: Int): Map<String, Any?> {
            expectChar('{')
            skipWhitespace()
            val map = LinkedHashMap<String, Any?>()
            if (peek('}')) {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expectChar(':')
                map[key] = parseValue(depth)
                skipWhitespace()
                when {
                    peek(',') -> pos++
                    peek('}') -> {
                        pos++
                        return map
                    }
                    else -> throw IllegalArgumentException("Expected ',' or '}' in JSON object at offset $pos")
                }
            }
        }

        private fun parseArray(depth: Int): List<Any?> {
            expectChar('[')
            skipWhitespace()
            val list = ArrayList<Any?>()
            if (peek(']')) {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue(depth))
                skipWhitespace()
                when {
                    peek(',') -> pos++
                    peek(']') -> {
                        pos++
                        return list
                    }
                    else -> throw IllegalArgumentException("Expected ',' or ']' in JSON array at offset $pos")
                }
            }
        }

        private fun parseString(): String {
            expectChar('"')
            val out = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                when {
                    ch == '"' -> return out.toString()
                    ch == '\\' -> {
                        if (pos >= src.length) throw IllegalArgumentException("Unterminated JSON escape")
                        when (val esc = src[pos++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) throw IllegalArgumentException("Truncated \\u escape")
                                val hex = src.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw IllegalArgumentException("Invalid \\u escape '$hex'")
                                out.append(code.toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Unsupported JSON escape '\\$esc'")
                        }
                    }
                    ch.code < 0x20 -> throw IllegalArgumentException("Unescaped control character in JSON string")
                    else -> out.append(ch)
                }
                if (out.length > MAX_JSON_STRING_CHARS) {
                    throw IllegalArgumentException("JSON string exceeds safety bound")
                }
            }
            throw IllegalArgumentException("Unterminated JSON string")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (peek('-')) pos++
            if (pos >= src.length || src[pos] !in '0'..'9') {
                throw IllegalArgumentException("Invalid JSON number at offset $start")
            }
            while (pos < src.length && src[pos] in '0'..'9') pos++
            var isFloat = false
            if (peek('.')) {
                isFloat = true
                pos++
                if (pos >= src.length || src[pos] !in '0'..'9') {
                    throw IllegalArgumentException("Invalid decimal fraction at offset $start")
                }
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            if (peek('e') || peek('E')) {
                isFloat = true
                pos++
                if (peek('+') || peek('-')) pos++
                if (pos >= src.length || src[pos] !in '0'..'9') {
                    throw IllegalArgumentException("Invalid exponent at offset $start")
                }
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            val token = src.substring(start, pos)
            return if (isFloat) {
                token.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid float '$token'")
            } else {
                token.toLongOrNull() ?: throw IllegalArgumentException("Invalid integer '$token'")
            }
        }

        private fun <T> consumeLiteral(literal: String, value: T): T {
            if (!src.startsWith(literal, pos)) {
                throw IllegalArgumentException("Expected '$literal' at offset $pos")
            }
            pos += literal.length
            return value
        }

        private fun skipWhitespace() {
            while (pos < src.length) {
                val ch = src[pos]
                if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n') pos++ else break
            }
        }

        private fun peek(expected: Char): Boolean = pos < src.length && src[pos] == expected

        private fun expectChar(expected: Char) {
            if (pos >= src.length || src[pos] != expected) {
                throw IllegalArgumentException("Expected '$expected' at offset $pos")
            }
            pos++
        }
    }
}
