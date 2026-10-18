package com.jm.reader.data.stats

import kotlin.random.Random

/**
 * Weighted tag selection for the "guess you like" entry.
 *
 * Kept pure (no Android, no coroutines) so the distribution can be unit tested: the UI asks for a
 * tag, then searches that tag and picks one work from the result. Weight = number of distinct
 * pages read in works carrying that tag, which makes a favourite tag more likely without making it
 * the only possible outcome.
 */
object GuessEngine {

    /**
     * Picks a tag with probability `count / total`.
     *
     * Ordering of [counts] does not bias the result: the random offset walks the weights, it does
     * not take the first entry that "fits".
     */
    fun pickTag(counts: Map<String, Int>, random: Random = Random.Default): String? {
        val positive = counts.filterValues { it > 0 }
        val total = positive.values.sum()
        if (total <= 0) return null
        var offset = random.nextInt(total)
        for ((tag, weight) in positive) {
            if (offset < weight) return tag
            offset -= weight
        }
        return positive.keys.lastOrNull()
    }

    /**
     * Tags ordered by weight, ties broken alphabetically.
     *
     * Used as the retry order when a tag search comes back empty: try the next-heaviest taste
     * instead of falling straight back to a blind random pick.
     */
    fun rankedTags(counts: Map<String, Int>, limit: Int = Int.MAX_VALUE): List<String> =
        counts.entries
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }

    /** Share of the total weight, 0f..1f. */
    fun share(count: Int, total: Int): Float =
        if (total <= 0) 0f else (count.toFloat() / total).coerceIn(0f, 1f)
}
