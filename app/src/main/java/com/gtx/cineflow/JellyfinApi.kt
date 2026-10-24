package com.gtx.cineflow

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Jellyfin public server API adapter. Connect only to a server you own or are allowed to use. */
data class JellyfinLoginRequest(val Username: String, val Pw: String)
data class JellyfinUser(val Id: String = "", val Name: String = "")
data class JellyfinAuthResponse(val AccessToken: String = "", val User: JellyfinUser = JellyfinUser())
data class JellyfinViewsResponse(val Items: List<JellyfinLibrary> = emptyList())
data class JellyfinLibrary(val Id: String = "", val Name: String = "", val CollectionType: String? = null)
data class JellyfinItemsResponse(val Items: List<JellyfinItem> = emptyList())
data class JellyfinPlaybackReport(
    val ItemId: String,
    val PositionTicks: Long = 0L,
    val PlayMethod: String = "DirectStream",
    val IsPaused: Boolean = false,
    val IsMuted: Boolean = false,
    val CanSeek: Boolean = true
)
data class JellyfinItem(
    val Id: String = "", val Name: String = "", val Overview: String? = null,
    val ProductionYear: Int? = null, val CommunityRating: Double? = null,
    val Type: String? = null, val ImageTags: Map<String, String>? = null
) {
    fun toCineFlow(serverBase: String, token: String): Movie {
        val kind = if (Type.equals("Episode", true) || Type.equals("Series", true)) "TV" else "Movie"
        val poster = if (ImageTags?.containsKey("Primary") == true) "$serverBase/Items/$Id/Images/Primary?api_key=$token" else ""
        val backdrop = if (ImageTags?.containsKey("Backdrop") == true) "$serverBase/Items/$Id/Images/Backdrop?api_key=$token" else poster
        return Movie(
            id = "jellyfin:$Id", title = Name, overview = Overview.orEmpty(), posterUrl = poster,
            backdropUrl = backdrop, year = ProductionYear?.toString().orEmpty(),
            rating = CommunityRating?.let { String.format(java.util.Locale.US, "%.1f", it) }.orEmpty(),
            kind = kind, streamUrl = "$serverBase/Videos/$Id/stream?static=true&api_key=$token"
        )
    }
}
interface JellyfinApi {
    @POST("Users/AuthenticateByName") suspend fun login(@Header("X-Emby-Authorization") authorization: String, @Body body: JellyfinLoginRequest): JellyfinAuthResponse
    @GET("Users/{userId}/Views") suspend fun views(@Path("userId") userId: String, @Header("X-Emby-Token") token: String): JellyfinViewsResponse
    @POST("Sessions/Playing") suspend fun playbackStarted(@Header("X-Emby-Token") token: String, @Body report: JellyfinPlaybackReport)
    @POST("Sessions/Playing/Progress") suspend fun playbackProgress(@Header("X-Emby-Token") token: String, @Body report: JellyfinPlaybackReport)
    @POST("Sessions/Playing/Stopped") suspend fun playbackStopped(@Header("X-Emby-Token") token: String, @Body report: JellyfinPlaybackReport)
    @GET("Users/{userId}/Items") suspend fun items(@Path("userId") userId: String, @Header("X-Emby-Token") token: String, @Query("ParentId") parentId: String? = null, @Query("Recursive") recursive: Boolean = true, @Query("IncludeItemTypes") types: String = "Movie,Series", @Query("Fields") fields: String = "Overview,ProductionYear,CommunityRating,PrimaryImageAspectRatio,BackdropImageTags", @Query("SortBy") sortBy: String = "SortName", @Query("Limit") limit: Int = 100): JellyfinItemsResponse
}
fun makeJellyfinApi(baseUrl: String): JellyfinApi {
    val base = baseUrl.trim().trimEnd('/') + "/"
    return Retrofit.Builder().baseUrl(base).addConverterFactory(GsonConverterFactory.create()).build().create(JellyfinApi::class.java)
}
fun jellyfinAuthorizationHeader(): String = "MediaBrowser Client=\"CineFlow\", Device=\"Android\", DeviceId=\"cineflow-android\", Version=\"1.0.0\""
