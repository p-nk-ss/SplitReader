package com.example.splitreader.data.translator.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface LibreTranslateApi {
    @POST
    suspend fun translate(@Url url: String, @Body body: LibreTranslateRequest): LibreTranslateResponse
}

data class LibreTranslateRequest(
    @SerializedName("q") val q: String,
    @SerializedName("source") val source: String,
    @SerializedName("target") val target: String,
    @SerializedName("format") val format: String = "text",
    @SerializedName("api_key") val apiKey: String? = null,
)

data class LibreTranslateResponse(
    @SerializedName("translatedText") val translatedText: String?,
)
