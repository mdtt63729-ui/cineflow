package com.gtx.cineflow.addons

/** Stable heuristic ranking. Missing properties receive no bonus or penalty. */
object AddonSourceRanker {
    fun rank(sources: List<AddonSource>): List<AddonSource> =
        sources.withIndex()
            .sortedWith(compareByDescending<IndexedValue<AddonSource>> { score(it.value) }.thenBy { it.index })
            .map { it.value }

    fun score(source: AddonSource): Int {
        var score = 0
        val label = listOfNotNull(source.title, source.name, source.displayName).joinToString(" ").lowercase()
        if (!source.isTorrent && !source.url.isNullOrBlank()) score += 100
        score += when {
            Regex("\\b(2160p|4k|uhd)\\b").containsMatchIn(label) -> 40
            Regex("\\b1080p\\b").containsMatchIn(label) -> 30
            Regex("\\b720p\\b").containsMatchIn(label) -> 20
            Regex("\\b480p\\b").containsMatchIn(label) -> 10
            else -> 0
        }
        val seeders = Regex("(?:seeders?|seeds?)\\s*[:=]?\\s*(\\d{1,7})")
            .find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (seeders != null) score += seeders.coerceIn(0, 1000) / 25
        if (Regex("\\b(cached|debrid cached)\\b").containsMatchIn(label)) score += 15
        if (Regex("\\b(cam|camrip|telesync|tsrip)\\b").containsMatchIn(label)) score -= 20
        return score
    }
}
