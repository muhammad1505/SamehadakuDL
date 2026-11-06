package app.samehadaku.dl.core

object RangeParser {

    /**
     * Python-compatible episode range parser (1-based).
     * "" -> all; "3" -> [3]; "2-5" -> [2..5]; "1,4,7-9" -> mixed.
     */
    fun parse(text: String, total: Int): List<Int> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return (1..total).toList()

        val indices = linkedSetOf<Int>()
        for (rawPart in trimmed.split(',')) {
            val part = rawPart.trim()
            if (part.isEmpty()) continue
            if ("-" in part) {
                val first = part.substringBefore('-')
                val rest = part.substringAfter('-')
                if (first.trim().isNotEmpty() && first.trim().all(Char::isDigit) &&
                    rest.trim().isNotEmpty() && rest.trim().all(Char::isDigit)
                ) {
                    val start = first.trim().toInt()
                    val end = rest.trim().toInt()
                    if (start <= end) {
                        for (value in start..end) indices.add(value)
                    }
                }
            } else if (part.isNotEmpty() && part.all(Char::isDigit)) {
                indices.add(part.toInt())
            }
        }

        return indices.filter { it in 1..total }.sorted()
    }
}
