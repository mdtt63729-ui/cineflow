package com.gtx.cineflow.addons

/**
 * Public default add-ons installed automatically on first launch so the user never has to add
 * them by hand. These are third-party public manifests, not a copy of anyone's personal Stremio
 * profile.
 *
 * Installation is best-effort and non-destructive: unreachable endpoints are skipped, and an
 * add-on the user already has (including one they disabled) is never re-installed or re-enabled.
 * Availability, configuration and legality vary by provider and region — a manifest that answers
 * here is not a claim that its streams are authorized in your country.
 */
data class BuiltInAddonSpec(
    val baseUrl: String,
    val purpose: String
)

object BuiltInAddonRegistry {
    /**
     * Ordered best-first: the home screen loads catalogues from the first few enabled add-ons,
     * so the most reliable catalogues are placed at the top.
     */
    val addons: List<BuiltInAddonSpec> = listOf(
        // --- Metadata, catalogues and subtitles ---
        BuiltInAddonSpec("https://v3-cinemeta.strem.io", "Movie and series metadata"),
        BuiltInAddonSpec("https://94c8cb9f702d-tmdb-addon.baby-beamup.club", "TMDB metadata and catalogues"),
        BuiltInAddonSpec("https://marvel-chrono-addon.houssam20.workers.dev", "Marvel chronological catalogues"),
        BuiltInAddonSpec("https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club", "Netflix catalogue"),
        BuiltInAddonSpec("https://plaviometa.vercel.app", "Metadata catalogue"),
        BuiltInAddonSpec("https://v3-channels.strem.io", "YouTube channel catalogue"),
        BuiltInAddonSpec("https://watchhub.strem.io", "Legal availability discovery"),
        BuiltInAddonSpec("https://opensubtitles-v3.strem.io", "Subtitle search"),

        // --- Torrent / debrid stream add-ons (most need a debrid key in their own settings) ---
        BuiltInAddonSpec("https://torrentio.strem.fun", "Torrent and debrid streams"),
        BuiltInAddonSpec("https://comet.elfhosted.com", "Torrent and debrid streams"),
        BuiltInAddonSpec("https://mediafusion.elfhosted.com", "Torrent and debrid streams"),
        BuiltInAddonSpec("https://aiostreams.elfhosted.com/stremio", "Stream aggregator"),
        BuiltInAddonSpec("https://peerflix.mov", "Torrent streams"),
        BuiltInAddonSpec("https://torrentsdb.com", "Torrent streams"),
        BuiltInAddonSpec("https://torrentclaw.com/api/stremio", "Torrent streams"),
        BuiltInAddonSpec("https://thepiratebay-plus.strem.fun", "Torrent streams"),
        BuiltInAddonSpec("https://150203dd784e-cinetorrent-addon.baby-beamup.club", "Torrent streams"),

        // --- Direct / regional stream add-ons ---
        BuiltInAddonSpec("https://flixnest.app/flix-streams", "Stream catalogue"),
        BuiltInAddonSpec("https://fenixflix.fenixhub.online", "Stream catalogue"),
        BuiltInAddonSpec("https://hdhub.thevolecitor.qzz.io/eyJ0b3Jib3giOiJ1bnNldCIsInF1YWxpdGllcyI6IjIxNjBwLDEwODBwLDcyMHAiLCJzb3J0IjoiZGVzYyJ9", "HD stream catalogue"),
        BuiltInAddonSpec("https://horror.debrid.in", "Horror stream catalogue"),
        BuiltInAddonSpec("https://hd.plavio.site", "HD stream catalogue"),
        BuiltInAddonSpec("https://stream.pencarimovie.com/66ff097863dedb7ccd8401d5ffe616f3", "Stream catalogue"),
        BuiltInAddonSpec("https://fun.3dmovies.workers.dev", "3D movie catalogue"),
        BuiltInAddonSpec("https://cncverse.dpdns.org", "Stream catalogue"),
        BuiltInAddonSpec("https://fayatv.stravo.site/d", "TV catalogue"),
        BuiltInAddonSpec("https://pengu.uk", "Stream catalogue"),
        BuiltInAddonSpec("https://tvvoo.hayd.uk/cfg-it-uk-fr", "TV catalogue"),
        BuiltInAddonSpec("https://icv.stremio-italia.eu", "Italian catalogue"),
        BuiltInAddonSpec("https://balkanio.cloud", "Balkan catalogue"),
        BuiltInAddonSpec("https://lat-add.lzid.me/1mgpZVfBxAtZkeGzXkVBcVMkawBkRXCS", "Latino catalogue"),
        BuiltInAddonSpec("https://asa.00696900.xyz", "Stream catalogue"),
        BuiltInAddonSpec("https://inmax-tmv.onrender.com", "Stream catalogue"),
        BuiltInAddonSpec("https://pipe.boringways.workers.dev", "Stream catalogue"),
        BuiltInAddonSpec("https://xclub-stremio.vercel.app", "Stream catalogue"),
        BuiltInAddonSpec("https://da5f663b4690-minhatv.baby-beamup.club", "TV catalogue"),

        // --- Anime ---
        BuiltInAddonSpec("https://streamvix.hayd.uk/%7B%22tmdbApiKey%22%3A%22%22%2C%22mediaFlowProxyUrl%22%3A%22%22%2C%22mediaFlowProxyPassword%22%3A%22%22%2C%22animeunityEnabled%22%3A%22on%22%2C%22animesaturnEnabled%22%3A%22on%22%2C%22animeworldEnabled%22%3A%22on%22%7D", "Anime catalogue"),
        BuiltInAddonSpec("https://meteorfortheweebs.midnightignite.me/stremio", "Anime catalogue"),
        BuiltInAddonSpec("https://c5541ffce7d3-aniscraper.baby-beamup.club", "Anime catalogue"),

        // --- Misc ---
        BuiltInAddonSpec("https://ytztvio.galacticcapsule.workers.dev", "YouTube catalogue"),
        BuiltInAddonSpec("https://skyline-webcams-stremio.lsoares.workers.dev", "Live webcam streams"),
        BuiltInAddonSpec("https://lsoares.github.io/fireplace", "Ambient fireplace video"),
        BuiltInAddonSpec("https://ltan.onrender.com/addon/eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJzbGlyZW5kZXYiLCJleHAiOjE3OTMwNzc1ODd9.9vAVsJ-3IKwDbub-YtWxRn8pszDvnM1B5zYwDQK1nG4", "Stream catalogue"),
    )
}
