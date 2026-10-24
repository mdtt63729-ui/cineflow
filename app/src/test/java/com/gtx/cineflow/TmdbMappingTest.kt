package com.gtx.cineflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbMappingTest {
    @Test fun mapsMovieMetadataWithoutInventingPlaybackUrl() {
        val mapped = TmdbItem(
            id = 42,
            title = "Example Film",
            overview = "A test synopsis",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            releaseDate = "2024-05-06",
            voteAverage = 8.25,
            mediaType = "movie"
        ).toMovie()

        assertEquals("tmdb:movie:42", mapped.id)
        assertEquals("Example Film", mapped.title)
        assertEquals("2024", mapped.year)
        assertEquals("8.3", mapped.rating)
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", mapped.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/original/backdrop.jpg", mapped.backdropUrl)
        assertTrue("TMDB metadata must not imply stream availability", mapped.streamUrl.isBlank())
    }

    @Test fun mapsTvNameAndFirstAirDate() {
        val mapped = TmdbItem(
            id = 9,
            name = "Example Series",
            firstAirDate = "2021-01-02",
            mediaType = "tv"
        ).toMovie()

        assertEquals("Example Series", mapped.title)
        assertEquals("2021", mapped.year)
        assertEquals("TV", mapped.kind)
    }
}
