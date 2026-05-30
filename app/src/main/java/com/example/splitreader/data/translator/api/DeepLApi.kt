package com.example.splitreader.data.translator.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

interface DeepLApi {
    @FormUrlEncoded
    @POST("v2/translate")
    suspend fun translate(
        @Header("Authorization") authHeader: String,
        @Field("text") text: String,
        @Field("source_lang") sourceLang: String,
        @Field("target_lang") targetLang: String,
    ): DeepLResponse
}

data class DeepLResponse(
    @SerializedName("translations") val translations: List<DeepLTranslation> = emptyList(),
)

data class DeepLTranslation(
    @SerializedName("text") val text: String?,
    @SerializedName("detected_source_language") val detectedSourceLanguage: String?,
)
