package eu.kanade.tachiyomi.ui.library

/**
 * Encoding helpers for the custom-series library preference. Each membership is stored as a single
 * string `"<entryId>:<seriesName>"` inside a preference `Set<String>`, mirroring the pinned-ids
 * preference. The entry id is always numeric, so splitting on the FIRST ':' is unambiguous even if
 * the series name itself contains ':'.
 */
object SeriesGrouping {
    private const val SEP = ':'

    /** Decodes the raw preference set into a map of entry id -> series name. */
    fun decode(raw: Set<String>): Map<Long, String> = raw.mapNotNull { entry ->
        val i = entry.indexOf(SEP)
        if (i <= 0) return@mapNotNull null
        val id = entry.substring(0, i).toLongOrNull() ?: return@mapNotNull null
        id to entry.substring(i + 1)
    }.toMap()

    /** Encodes one membership entry. */
    fun encode(id: Long, name: String): String = "$id$SEP$name"

    /**
     * Existing series names that actually form a group (≥2 members), sorted for display. A leftover
     * 1-member remnant isn't a real group and shouldn't be suggested (it wouldn't even render as one).
     */
    fun seriesNames(raw: Set<String>): List<String> =
        decode(raw).values.groupingBy { it }.eachCount().filterValues { it >= 2 }.keys.sorted()

    /**
     * Returns [raw] with [ids] (re)assigned to [name], dropping any previous membership for them.
     */
    fun assign(raw: Set<String>, ids: List<Long>, name: String): Set<String> {
        val idSet = ids.toSet()
        return raw.filterNot { entry ->
            val i = entry.indexOf(SEP)
            i > 0 && entry.substring(0, i).toLongOrNull() in idSet
        }.toSet() + ids.map { encode(it, name) }
    }

    /** Returns [raw] with any membership for [ids] removed (ungroup). */
    fun remove(raw: Set<String>, ids: List<Long>): Set<String> {
        val idSet = ids.toSet()
        return raw.filterNot { entry ->
            val i = entry.indexOf(SEP)
            i > 0 && entry.substring(0, i).toLongOrNull() in idSet
        }.toSet()
    }
}
