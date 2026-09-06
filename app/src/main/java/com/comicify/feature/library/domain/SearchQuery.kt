package com.comicify.feature.library.domain

import java.text.Normalizer

class SearchQuery private constructor(private val terms: List<SearchTerm>) {

    fun matches(comic: LibraryComic, now: Long): Boolean = terms.all { it.matches(comic, now) }

    companion object {
        fun parse(text: String): SearchQuery = SearchQuery(tokenize(text).mapNotNull(::parseTerm))
    }
}

private sealed interface SearchTerm {
    fun matches(comic: LibraryComic, now: Long): Boolean
}

private class FreeTerm(private val needle: String) : SearchTerm {
    override fun matches(comic: LibraryComic, now: Long): Boolean =
        freeTextOf(comic).any { it.contains(needle) }
}

private class TextTerm(private val field: TextField, private val needle: String) : SearchTerm {
    override fun matches(comic: LibraryComic, now: Long): Boolean =
        field.select(comic)?.let { fold(it).contains(needle) } == true
}

private class YearTerm(private val comparison: Comparison, private val year: Int) : SearchTerm {
    override fun matches(comic: LibraryComic, now: Long): Boolean =
        comic.year?.let { comparison.holds(it, year) } == true
}

private class RatingTerm(private val comparison: Comparison, private val rating: Int) : SearchTerm {
    override fun matches(comic: LibraryComic, now: Long): Boolean = comparison.holds(comic.rating, rating)
}

private class AddedTerm(private val days: Int) : SearchTerm {
    override fun matches(comic: LibraryComic, now: Long): Boolean =
        comic.addedAt >= now - days * MILLIS_PER_DAY
}

private class StateTerm(private val state: ReadState) : SearchTerm {
    override fun matches(comic: LibraryComic, now: Long): Boolean = state.holds(comic)
}

private enum class TextField(val keys: List<String>, val select: (LibraryComic) -> String?) {
    SERIES(listOf("series", "serie"), { it.series }),
    STORY_TITLE(listOf("title", "titulo"), { it.storyTitle }),
    WRITER(listOf("writer", "guionista"), { it.writer }),
    PENCILLER(listOf("penciller", "dibujante"), { it.penciller }),
    INKER(listOf("inker", "entintador"), { it.inker }),
    COLORIST(listOf("colorist", "color"), { it.colorist }),
    PUBLISHER(listOf("publisher", "editorial"), { it.publisher }),
}

private enum class Comparison(val symbol: String) {
    GREATER_OR_EQUAL(">="),
    LESS_OR_EQUAL("<="),
    GREATER(">"),
    LESS("<"),
    EQUAL(":");

    fun holds(left: Int, right: Int): Boolean = when (this) {
        GREATER_OR_EQUAL -> left >= right
        LESS_OR_EQUAL -> left <= right
        GREATER -> left > right
        LESS -> left < right
        EQUAL -> left == right
    }
}

private enum class ReadState(val key: String, val holds: (LibraryComic) -> Boolean) {
    READING("reading", { it.pageIndex > 0 && !it.completed }),
    UNREAD("unread", { !it.completed }),
    READ("read", { it.completed }),
}

private const val YEAR_KEY = "year"
private const val YEAR_ALIAS = "ano"
private const val RATING_KEY = "rating"
private const val RATING_ALIAS = "valoracion"
private const val AT_LEAST_SUFFIX = '+'
private const val ADDED_KEY = "added"
private const val STATE_KEY = "is"
private const val DAY_SUFFIX = 'd'
private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
private const val QUOTE = '"'

private val separator = Regex(Comparison.entries.joinToString("|") { Regex.escape(it.symbol) })
private val combiningMarks = Regex("\\p{Mn}+")

private fun tokenize(text: String): List<String> {
    val tokens = mutableListOf<String>()
    val token = StringBuilder()
    var quoted = false
    text.forEach { character ->
        when {
            character == QUOTE -> quoted = !quoted
            character.isWhitespace() && !quoted -> {
                if (token.isNotEmpty()) tokens.add(token.toString())
                token.clear()
            }
            else -> token.append(character)
        }
    }
    if (token.isNotEmpty()) tokens.add(token.toString())
    return tokens
}

private fun parseTerm(token: String): SearchTerm? {
    val match = separator.find(token) ?: return FreeTerm(fold(token))
    val key = fold(token.take(match.range.first))
    if (!isFieldKey(key)) return FreeTerm(fold(token))
    val value = token.substring(match.range.last + 1)
    if (value.isBlank()) return null
    val comparison = Comparison.entries.first { it.symbol == match.value }
    return fieldTerm(key, comparison, value) ?: FreeTerm(fold(token))
}

private fun isFieldKey(key: String): Boolean =
    key == YEAR_KEY || key == YEAR_ALIAS || key == RATING_KEY || key == RATING_ALIAS ||
        key == ADDED_KEY || key == STATE_KEY || textField(key) != null

private fun textField(key: String): TextField? = TextField.entries.firstOrNull { key in it.keys }

private fun fieldTerm(key: String, comparison: Comparison, value: String): SearchTerm? = when {
    key == YEAR_KEY || key == YEAR_ALIAS -> value.toIntOrNull()?.let { YearTerm(comparison, it) }
    key == RATING_KEY || key == RATING_ALIAS -> ratingTerm(comparison, value)
    comparison != Comparison.EQUAL -> null
    key == ADDED_KEY -> daysWithin(value)?.let(::AddedTerm)
    key == STATE_KEY -> ReadState.entries.firstOrNull { it.key == fold(value) }?.let(::StateTerm)
    else -> textField(key)?.let { TextTerm(it, fold(value)) }
}

private fun ratingTerm(comparison: Comparison, value: String): SearchTerm? {
    val atLeast = comparison == Comparison.EQUAL && value.last() == AT_LEAST_SUFFIX
    val rating = (if (atLeast) value.dropLast(1) else value).toIntOrNull() ?: return null
    return RatingTerm(if (atLeast) Comparison.GREATER_OR_EQUAL else comparison, rating)
}

private fun daysWithin(value: String): Int? =
    value.takeIf { it.last().lowercaseChar() == DAY_SUFFIX }?.dropLast(1)?.toIntOrNull()?.takeIf { it >= 0 }

private fun freeTextOf(comic: LibraryComic): List<String> =
    listOfNotNull(
        comic.title,
        comic.series,
        comic.storyTitle,
        comic.writer,
        comic.penciller,
        comic.inker,
        comic.colorist,
        comic.publisher,
        comic.displayName,
    ).map(::fold)

private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(combiningMarks, "").lowercase()
