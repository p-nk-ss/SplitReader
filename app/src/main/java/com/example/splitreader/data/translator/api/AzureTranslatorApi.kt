package com.example.splitreader.data.translator.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface AzureTranslatorApi {
    @POST("translate")
    suspend fun translate(
        @Query("api-version") apiVersion: String = "3.0",
        @Query("from") from: String,
        @Query("to") to: String,
        @Header("Ocp-Apim-Subscription-Key") key: String,
        @Header("Ocp-Apim-Subscription-Region") region: String?, // null => header omitted
        @Body body: List<AzureTranslateItem>,
    ): List<AzureTranslateResult>
}

data class AzureTranslateItem(@SerializedName("Text") val text: String)

data class AzureTranslateResult(
    @SerializedName("translations") val translations: List<AzureTranslation> = emptyList(),
)

data class AzureTranslation(
    @SerializedName("text") val text: String?,
    @SerializedName("to") val to: String?,
)
