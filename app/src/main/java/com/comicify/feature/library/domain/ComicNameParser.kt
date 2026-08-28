package com.comicify.feature.library.domain

object ComicNameParser {

    private const val MIN_YEAR = 1900
    private const val MAX_YEAR = 2099

    private val parenGroup = Regex("""\(([^)]*)\)""")
    private val punctuation = Regex("""[._\-]+""")
    private val multiSpace = Regex("""\s+""")
    private val strictYear = Regex("""^(19|20)\d{2}$""")
    private val embeddedYear = Regex("""(19|20)\d{2}""")
    private val hashIssue = Regex("""^#0*(\d{1,6})$""")
    private val plainNumber = Regex("""^0*(\d{1,6})$""")
    private val volumeMarker = Regex("""^[vV](?:ol(?:ume)?)?\.?$""")
    private val gluedIssue = Regex("""^([A-Za-z]{3,})(\d{1,4})$""")

    fun parse(fileName: String): ParsedComicName {
        val base = fileName.substringBeforeLast('.', fileName)
        val parenYear = parenGroup.findAll(base)
            .mapNotNull { yearWithin(it.groupValues[1]) }
            .firstOrNull()
        val normalized = parenGroup.replace(base, " ")
            .replace(punctuation, " ")
            .replace(multiSpace, " ")
            .trim()
        val tokens = splitGluedIssue(normalized.split(' ').filter { it.isNotBlank() })

        val yearIndex = if (parenYear != null) -1 else tokens.indexOfLast { strictYear.matches(it) }
        val year = parenYear ?: yearIndex.takeIf { it >= 0 }?.let { tokens[it].toInt() }

        val hashIndex = tokens.indexOfFirst { hashIssue.matches(it) }
        val issueIndex = when {
            hashIndex >= 0 -> hashIndex
            else -> tokens.indices.lastOrNull { it != yearIndex && plainNumber.matches(tokens[it]) } ?: -1
        }
        val issueNumber = issueIndex.takeIf { it >= 0 }?.let { numberFrom(tokens[it]) }

        val series = seriesFrom(tokens, issueIndex, yearIndex, base)
        return ParsedComicName(series = series, issueNumber = issueNumber, year = year)
    }

    private fun seriesFrom(tokens: List<String>, issueIndex: Int, yearIndex: Int, base: String): String {
        val boundary = listOfNotNull(issueIndex.takeIf { it >= 0 }, yearIndex.takeIf { it >= 0 }).minOrNull()
            ?: tokens.size
        val head = tokens.take(boundary)
        val volumeCut = head.indexOfFirst { volumeMarker.matches(it) }
        val trimmed = if (volumeCut >= 0) head.take(volumeCut) else head
        val series = trimmed.joinToString(" ").trim()
        if (series.isNotBlank()) return series
        val fallback = tokens.filterIndexed { index, _ -> index != issueIndex && index != yearIndex }
            .joinToString(" ")
            .trim()
        return fallback.ifBlank { base.trim() }
    }

    private fun splitGluedIssue(tokens: List<String>): List<String> {
        if (tokens.any { hashIssue.matches(it) || plainNumber.matches(it) }) return tokens
        val glued = tokens.lastOrNull()?.let(gluedIssue::find) ?: return tokens
        return tokens.dropLast(1) + glued.groupValues[1] + glued.groupValues[2]
    }

    private fun numberFrom(token: String): Int? =
        hashIssue.find(token)?.groupValues?.get(1)?.toIntOrNull()
            ?: plainNumber.find(token)?.groupValues?.get(1)?.toIntOrNull()

    private fun yearWithin(text: String): Int? =
        embeddedYear.find(text)?.value?.toIntOrNull()?.takeIf { it in MIN_YEAR..MAX_YEAR }
}
