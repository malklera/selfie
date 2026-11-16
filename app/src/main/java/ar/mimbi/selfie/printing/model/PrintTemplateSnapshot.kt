package ar.mimbi.selfie.printing.model

object PrintTemplateSnapshot {

    private val KNOWN_TOP_LEVEL_KEYS = setOf(
        "id",
        "description",
        "version",
        "quality",
        "colorMode",
        "widthCm",
        "heightCm",
        "pageType",
        "slots"
    )

    private val KNOWN_SLOT_KEYS = setOf(
        "left",
        "top",
        "width",
        "height"
    )

    fun serialize(template: PrintTemplate): String {
        return buildString {
            append("{")
            append("\"id\":").append(template.id).append(",")
            append("\"description\":\"").append(escapeJson(template.description)).append("\",")
            append("\"version\":").append(template.version).append(",")
            append("\"quality\":\"").append(escapeJson(template.quality)).append("\",")
            append("\"colorMode\":\"").append(escapeJson(template.colorMode)).append("\",")
            append("\"widthCm\":").append(template.widthCm).append(",")
            append("\"heightCm\":").append(template.heightCm).append(",")
            append("\"pageType\":\"").append(escapeJson(template.pageType)).append("\",")
            append("\"slots\":[")
            template.slots.forEachIndexed { index, slot ->
                if (index > 0) append(",")
                append("{\"left\":").append(slot.left)
                append(",\"top\":").append(slot.top)
                append(",\"width\":").append(slot.width)
                append(",\"height\":").append(slot.height).append("}")
            }
            append("]")
            append("}")
        }
    }

    fun deserialize(jsonString: String): PrintTemplate? {
        if (jsonString.isBlank()) return null

        return try {
            val parser = SimpleJsonParser(jsonString)
            val parsedRoot = parser.parse() as? Map<*, *> ?: return null

            // Validate top-level keys
            for (key in parsedRoot.keys) {
                if (key !is String || key !in KNOWN_TOP_LEVEL_KEYS) {
                    return null // Unknown field at top level!
                }
            }

            val id = (parsedRoot["id"] as? Number)?.toInt() ?: return null
            val description = (parsedRoot["description"] as? String) ?: ""
            val version = (parsedRoot["version"] as? Number)?.toInt() ?: 1
            val quality = (parsedRoot["quality"] as? String) ?: "Alta (300 DPI)"
            val colorMode = (parsedRoot["colorMode"] as? String) ?: "Color"
            val widthCm = (parsedRoot["widthCm"] as? Number)?.toFloat() ?: 21.0f
            val heightCm = (parsedRoot["heightCm"] as? Number)?.toFloat() ?: 29.7f
            val pageType = (parsedRoot["pageType"] as? String) ?: "A4"

            val slots = mutableListOf<PhotoSlot>()
            val slotsRaw = parsedRoot["slots"]
            if (slotsRaw != null) {
                val slotsList = slotsRaw as? List<*> ?: return null
                for (item in slotsList) {
                    val slotMap = item as? Map<*, *> ?: return null
                    // Validate slot keys
                    for (slotKey in slotMap.keys) {
                        if (slotKey !is String || slotKey !in KNOWN_SLOT_KEYS) {
                            return null // Unknown field in slot!
                        }
                    }
                    val left = (slotMap["left"] as? Number)?.toFloat() ?: return null
                    val top = (slotMap["top"] as? Number)?.toFloat() ?: return null
                    val width = (slotMap["width"] as? Number)?.toFloat() ?: return null
                    val height = (slotMap["height"] as? Number)?.toFloat() ?: return null
                    slots.add(PhotoSlot(left = left, top = top, width = width, height = height))
                }
            }

            PrintTemplate(
                id = id,
                description = description,
                slots = slots,
                version = version,
                quality = quality,
                colorMode = colorMode,
                widthCm = widthCm,
                heightCm = heightCm,
                pageType = pageType
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private class SimpleJsonParser(private val json: String) {
        private var pos = 0

        fun parse(): Any? {
            skipWhitespace()
            if (pos >= json.length) return null
            val result = parseValue()
            skipWhitespace()
            if (pos < json.length) return null // Trailing content
            return result
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= json.length) return null
            return when (json[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> {
                    parseNull()
                    null
                }
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?>? {
            if (pos >= json.length || json[pos] != '{') return null
            pos++ // skip '{'
            val map = mutableMapOf<String, Any?>()
            skipWhitespace()
            if (pos < json.length && json[pos] == '}') {
                pos++
                return map
            }
            while (pos < json.length) {
                skipWhitespace()
                if (pos >= json.length || json[pos] != '"') return null
                val key = parseString() ?: return null
                skipWhitespace()
                if (pos >= json.length || json[pos] != ':') return null
                pos++ // skip ':'
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (pos >= json.length) return null
                if (json[pos] == '}') {
                    pos++
                    return map
                } else if (json[pos] == ',') {
                    pos++
                } else {
                    return null
                }
            }
            return null
        }

        private fun parseArray(): List<Any?>? {
            if (pos >= json.length || json[pos] != '[') return null
            pos++ // skip '['
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (pos < json.length && json[pos] == ']') {
                pos++
                return list
            }
            while (pos < json.length) {
                val value = parseValue()
                list.add(value)
                skipWhitespace()
                if (pos >= json.length) return null
                if (json[pos] == ']') {
                    pos++
                    return list
                } else if (json[pos] == ',') {
                    pos++
                } else {
                    return null
                }
            }
            return null
        }

        private fun parseString(): String? {
            if (pos >= json.length || json[pos] != '"') return null
            pos++ // skip opening '"'
            val sb = StringBuilder()
            while (pos < json.length) {
                val ch = json[pos++]
                if (ch == '"') return sb.toString()
                if (ch == '\\') {
                    if (pos >= json.length) return null
                    when (val escaped = json[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > json.length) return null
                            val hex = json.substring(pos, pos + 4)
                            pos += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> sb.append(escaped)
                    }
                } else {
                    sb.append(ch)
                }
            }
            return null
        }

        private fun parseBoolean(): Boolean? {
            if (json.startsWith("true", pos)) {
                pos += 4
                return true
            } else if (json.startsWith("false", pos)) {
                pos += 5
                return false
            }
            return null
        }

        private fun parseNull() {
            if (json.startsWith("null", pos)) {
                pos += 4
            }
        }

        private fun parseNumber(): Number? {
            val start = pos
            if (pos < json.length && json[pos] == '-') pos++
            val startDigits = pos
            while (pos < json.length && json[pos].isDigit()) pos++
            if (pos == startDigits) return null
            var isDouble = false
            if (pos < json.length && json[pos] == '.') {
                isDouble = true
                pos++
                val dotDigits = pos
                while (pos < json.length && json[pos].isDigit()) pos++
                if (pos == dotDigits) return null
            }
            if (pos < json.length && (json[pos] == 'e' || json[pos] == 'E')) {
                isDouble = true
                pos++
                if (pos < json.length && (json[pos] == '+' || json[pos] == '-')) pos++
                val expDigits = pos
                while (pos < json.length && json[pos].isDigit()) pos++
                if (pos == expDigits) return null
            }
            val numStr = json.substring(start, pos)
            return if (isDouble) numStr.toDoubleOrNull() else numStr.toLongOrNull()
        }

        private fun skipWhitespace() {
            while (pos < json.length && (json[pos] == ' ' || json[pos] == '\t' || json[pos] == '\n' || json[pos] == '\r')) {
                pos++
            }
        }
    }
}

