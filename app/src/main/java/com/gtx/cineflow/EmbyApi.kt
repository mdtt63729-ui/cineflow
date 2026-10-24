package com.gtx.cineflow

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Emby public API adapter foundation. Connect only to a server you own or are authorized to access. */
data class EmbyLoginRequest(val Username: String, val Pw: String)
data class EmbyUser(val Id: String = "", val Name: String = "")
data class EmbyAuthResponse(val AccessToken: String = "", val User: EmbyUser = EmbyUser())
data class EmbyLibraryResponse(val Items: List<EmbyLibrary> = emptyList())
data class EmbyLibrary(val Id: String = "", val Name: String = "", val CollectionType: String? = null)
data class EmbyItemsResponse(val Items: List<EmbyItem> = emptyList())

data class EmbyItem(
    val Id: String = "",
    val Name: String = "",
    val Overview: String? = null,
    val ProductionYear: Int? = null,
    val CommunityRating: Double? = null,
    val Type: String? = null,
    val ImageTags: Map<String, String>? = null
) {
    /** Maps Emby metadata to CineFlow. The URL is for the user's own/authorized Emby server only. */
    fun toCineFlow(serverBase: String, token: String): Movie {
        val base = serverBase.trim().trimEnd('/')
        val kind = if (Type.equals("Series", true) || Type.equals("Episode", true)) "TV" else "Movie"
        val poster = if (ImageTags?.containsKey("Primary") == true) "$base/Items/$Id/Images/Primary?api_key=$token" else ""
        val backdrop = if (ImageTags?.containsKey("Backdrop") == true) "$base/Items/$Id/Images/Backdrop?api_key=$token" else poster
        return Movie(
            id = "emby:$Id", title = Name, overview = Overview.orEmpty(),
            posterUrl = poster, backdropUrl = backdrop,
            year = ProductionYear?.toString().orEmpty(),
            rating = CommunityRating?.let { String.format(java.util.Locale.US, "%.1f", it) }.orEmpty(),
            kind = kind, streamUrl = "$base/Videos/$Id/stream?static=true&api_key=$token"
        )
    }
}

interface EmbyApi {
    @POST("Users/AuthenticateByName")
    suspend fun login(
        @Header("X-Emby-Authorization") authorization: String,
        @Body body: EmbyLoginRequest
    ): EmbyAuthResponse

    @GET("Users/{userId}/Views")
    suspend fun views(
        @Path("userId") userId: String,
        @Header("X-Emby-Token") token: String
    ): EmbyLibraryResponse

    @GET("Users/{userId}/Items")
    suspend fun items(
        @Path("userId") userId: String,
        @Header("X-Emby-Token") token: String,
        @Query("ParentId") parentId: String? = null,
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") types: String = "Movie,Series",
        @Query("Fields") fields: String = "Overview,ProductionYear,CommunityRating,PrimaryImageAspectRatio,BackdropImageTags",
        @Query("SortBy") sortBy: String = "SortName",
        @Query("Limit") limit: Int = 100
    ): EmbyItemsResponse
}

fun makeEmbyApi(baseUrl: String): EmbyApi {
    val normalized = baseUrl.trim().trimEnd('/') + "/"
    require(normalized.startsWith("https://", ignoreCase = true)) {
        "Use an HTTPS Emby server URL for this integration."
    }
    return Retrofit.Builder()
        .baseUrl(normalized)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(EmbyApi::class.java)
}

fun embyAuthorizationHeader(): String =
    "MediaBrowser Client=\"CineFlow\", Device=\"Android\", DeviceId=\"cineflow-android\", Version=\"1.0.0\""
