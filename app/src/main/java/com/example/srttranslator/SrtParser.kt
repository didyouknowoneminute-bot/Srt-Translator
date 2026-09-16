package com.example.srttranslator

data class Subtitle(val number: Int, val timing: String, val text: String)

object SrtParser {
    private val timing = Regex("\\d{2}:\\d{2}:\\d{2},\\d{3} --> \\d{2}:\\d{2}:\\d{2},\\d{3}")
    fun parse(raw: String): List<Subtitle> = raw.trim().split(Regex("\\r?\\n\\s*\\r?\\n"))
        .mapNotNull { block ->
            val lines = block.lines(); val index = lines.indexOfFirst { timing.matches(it.trim()) }
            if (index < 1) null else Subtitle(lines[index - 1].trim().toIntOrNull() ?: return@mapNotNull null, lines[index].trim(), lines.drop(index + 1).joinToString("\n").trim())
        }
    fun render(items: List<Subtitle>): String = items.joinToString("\n\n") { "${it.number}\n${it.timing}\n${it.text}" } + "\n"
    fun splitThree(items: List<Subtitle>): List<List<Subtitle>> { val size = (items.size + 2) / 3; return listOf(items.take(size), items.drop(size).take(size), items.drop(size * 2)).filter { it.isNotEmpty() } }
}
