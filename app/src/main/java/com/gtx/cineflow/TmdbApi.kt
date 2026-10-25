package com.gtx.cineflow

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/** TMDB metadata adapter. It deliberately does not resolve or return video streams. */
// TMDB is metadata only: it never supplies a playable video URL.
data class TmdbListResponse(val results: List<TmdbItem> = emptyList())
data class TmdbItem(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    val overview: String = "",
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("original_language") val originalLanguage: String? = null,
    @SerializedName("origin_country") val originCountry: List<String> = emptyList()
)
interface TmdbApi {
    @GET("trending/all/day") suspend fun trending(@Query("api_key") key: String): TmdbListResponse
    @GET("movie/popular") suspend fun popularMovies(@Query("api_key") key: String): TmdbListResponse
    @GET("tv/popular") suspend fun popularTv(@Query("api_key") key: String): TmdbListResponse
    @GET("discover/movie") suspend fun indianMovies(
        @Query("api_key") key: String,
        @Query("language") language: String = "en-IN",
        @Query("region") region: String = "IN",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("with_origin_country") originCountry: String = "IN",
        @Query("with_original_language") originalLanguage: String = "hi|bn|ta|te|ml|kn|mr|pa",
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbListResponse
    @GET("discover/tv") suspend fun indianTv(
        @Query("api_key") key: String,
        @Query("language") language: String = "en-IN",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("with_origin_country") originCountry: String = "IN",
        @Query("with_original_language") originalLanguage: String = "hi|bn|ta|te|ml|kn|mr|pa",
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbListResponse
    @GET("search/multi") suspend fun search(@Query("api_key") key: String, @Query("query") query: String, @Query("include_adult") adult: Boolean = false): TmdbListResponse
}
fun makeTmdbApi(): TmdbApi = Retrofit.Builder()
    .baseUrl("https://api.themoviedb.org/3/")
    .addConverterFactory(GsonConverterFactory.create())
    .build().create(TmdbApi::class.java)
fun TmdbItem.toMovie(): Movie {
    val type = mediaType ?: if (title != null) "movie" else "tv"
    val release = releaseDate ?: firstAirDate.orEmpty()
    return Movie(
        id = "tmdb:$type:$id", title = title ?: name ?: "Untitled", overview = overview,
        posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }.orEmpty(),
        backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }.orEmpty(),
        year = release.take(4), rating = if (voteAverage > 0) "%.1f".format(java.util.Locale.US, voteAverage) else "—",
        kind = if (type == "tv") "TV" else "Movie", streamUrl = ""
    )
}

