package com.bump.visualizer

object LrcParser {
    private val timestamp = Regex("""\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]""")

    fun parse(input: String): List<LyricWord> {
        val timed = mutableListOf<Pair<Long, String>>()
        for (raw in input.lineSequence()) {
            val matches = timestamp.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val text = raw.substringAfterLast("]").trim()
            if (text.isBlank()) continue
            for (m in matches) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fraction = m.groupValues[3].padEnd(3, '0').ifBlank { "0" }.take(3).toLong()
                timed += (min * 60_000L + sec * 1_000L + fraction) to text
            }
        }
        timed.sortBy { it.first }
        if (timed.isEmpty()) return emptyList()

        val result = mutableListOf<LyricWord>()
        for (i in timed.indices) {
            val start = timed[i].first
            val next = timed.getOrNull(i + 1)?.first ?: (start + 2_500L)
            val words = timed[i].second.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) continue
            val slot = ((next - start).coerceAtLeast(180L)) / words.size
            words.forEachIndexed { index, word ->
                val s = start + slot * index
                val e = if (index == words.lastIndex) next else (s + slot).coerceAtMost(next)
                result += LyricWord(word, s, e.coerceAtLeast(s + 80L))
            }
        }
        return result
    }
}
