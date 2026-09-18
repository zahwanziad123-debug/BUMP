package com.bump.visualizer

object LyricTimeline {
    fun demo(): List<LyricWord> {
        val lines = listOf(
            "I don't wanna be alone",
            "I don't wanna be alone tonight",
            "I don't wanna know the truth",
            "I just wanna feel alive"
        )
        val words = mutableListOf<LyricWord>()
        var t = 0L
        for (line in lines) {
            for (word in line.split(" ")) {
                words += LyricWord(word, t, t + 520L)
                t += 560L
            }
            t += 700L
        }
        return words
    }
}
