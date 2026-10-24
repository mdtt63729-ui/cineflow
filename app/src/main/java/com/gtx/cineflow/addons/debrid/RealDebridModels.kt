package com.gtx.cineflow.addons.debrid

import com.google.gson.annotations.SerializedName

data class RealDebridUser(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("type") val accountType: String? = null,
    @SerializedName("premium") val premium: Int? = null,
    @SerializedName("expiration") val expiration: String? = null,
)

data class UnrestrictedLink(
    @SerializedName("id") val id: String? = null,
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("mimeType") val mimeType: String? = null,
    @SerializedName("filesize") val fileSize: Long? = null,
    @SerializedName("link") val link: String? = null,
    @SerializedName("host") val host: String? = null,
    @SerializedName("download") val download: String? = null,
)

class RealDebridException(message: String, val httpCode: Int? = null) : Exception(message)
