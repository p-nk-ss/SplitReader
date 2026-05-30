package com.example.splitreader.data.translator.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Query

interface GoogleCloudApi {
    @FormUrlEncoded
    @POST("language/translate/v2")
    suspend fun translate(
        @Query("key") apiKey: String,
        @Field("q") text: String,
        @Field("source") source: String,
        @Field("target") target: String,
        @Field("format") format: String = "text",
    ): GoogleCloudResponse
}

data class GoogleCloudResponse(
    @SerializedName("data") val data: GoogleCloudData?,
)

data class GoogleCloudData(
    @SerializedName("translations") val translations: List<GoogleCloudTranslation> = emptyList(),
)

data class GoogleCloudTranslation(
    @SerializedName("translatedText") val translatedText: String?,
)
