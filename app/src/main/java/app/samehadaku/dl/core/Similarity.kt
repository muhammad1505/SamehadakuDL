package app.samehadaku.dl.core

object Similarity {

    /**
     * Normalized similarity in [0, 1], equivalent in spirit to Python's
     * difflib.SequenceMatcher.ratio() for the episode-group tie-breaking in the parser.
     */
    fun ratio(left: String, right: String): Double {
        if (left == right) return 1.0
        val maxLength = maxOf(left.length, right.length)
        if (maxLength == 0) return 1.0
        return 1.0 - levenshtein(left, right).toDouble() / maxLength.toDouble()
    }

    fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        for (i in 1..left.length) {
            current[0] = i
            for (j in 1..right.length) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }
}
