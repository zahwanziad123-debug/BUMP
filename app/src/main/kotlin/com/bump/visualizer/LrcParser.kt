package com.bump.visualizer

object LrcParser {
    private val timestamp = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]""")

    fun parse(input: String): List<LyricWord> {
        val timed = mutableListOf<Pair<Long, String>>()
        for (raw in input.lineSequence()) {
            val matches = timestamp.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val text = raw.substringAfterLast("]").trim()
            if (text.isBlank()) continue

            for (m in matches) {
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val fractionText = m.groupValues[3]
                val millis = when {
                    fractionText.isBlank() -> 0L
                    fractionText.length == 1 -> fractionText.toLong() * 100L
                    fractionText.length == 2 -> fractionText.toLong() * 10L
                    else -> fractionText.take(3).toLong()
                }
                timed += (minutes * 60_000L + seconds * 1_000L + millis) to text
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

            val span = (next - start).coerceAtLeast(180L)
            val slot = (span / words.size).coerceAtLeast(80L)

            words.forEachIndexed { index, word ->
                val wordStart = start + slot * index
                val wordEnd = if (index == words.lastIndex) next else wordStart + slot
                result += LyricWord(word, wordStart, wordEnd.coerceAtLeast(wordStart + 80L))
            }
        }
        return result
    }
}
