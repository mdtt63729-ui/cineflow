package com.gtx.cineflow

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

/** Public Trakt catalogue endpoints only. No OAuth, account access, or user-data sync. */
data class TraktIds(
    val trakt: Long? = null,
    val slug: String? = null,
    val imdb: String? = null,
    val tmdb: Long? = null
)

data class TraktMovieRef(
    val title: String? = null,
    val year: Int? = null,
    val ids: TraktIds = TraktIds()
)

data class TraktShowRef(
    val title: String? = null,
    val year: Int? = null,
    val ids: TraktIds = TraktIds()
)

data class TraktTrendingMovie(
    val watchers: Int? = null,
    val movie: TraktMovieRef? = null
)

data class TraktTrendingShow(
    val watchers: Int? = null,
    val show: TraktShowRef? = null
)

data class TraktSearchMovie(
    val score: Double? = null,
    val type: String? = null,
    val movie: TraktMovieRef? = null
)

data class TraktSearchShow(
    val score: Double? = null,
    val type: String? = null,
    val show: TraktShowRef? = null
)

interface TraktApi {
    @Headers("trakt-api-version: 2")
    @GET("movies/trending")
    suspend fun trendingMovies(
        @Header("trakt-api-key") clientId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): List<TraktTrendingMovie>

    @Headers("trakt-api-version: 2")
    @GET("movies/popular")
    suspend fun popularMovies(
        @Header("trakt-api-key") clientId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): List<TraktMovieRef>

    @Headers("trakt-api-version: 2")
    @GET("shows/trending")
    suspend fun trendingShows(
        @Header("trakt-api-key") clientId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): List<TraktTrendingShow>

    @Headers("trakt-api-version: 2")
    @GET("shows/popular")
    suspend fun popularShows(
        @Header("trakt-api-key") clientId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): List<TraktShowRef>

    @Headers("trakt-api-version: 2")
    @GET("search/movie")
    suspend fun searchMovies(
        @Header("trakt-api-key") clientId: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): List<TraktSearchMovie>

    @Headers("trakt-api-version: 2")
    @GET("search/show")
    suspend fun searchShows(
        @Header("trakt-api-key") clientId: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): List<TraktSearchShow>
}

fun makeTraktApi(): TraktApi = Retrofit.Builder()
    .baseUrl("https://api.trakt.tv/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(TraktApi::class.java)

fun requireTraktClientId(clientId: String): String {
    require(clientId.isNotBlank()) { "Trakt Client ID is required for public catalogue API requests." }
    return clientId
}
