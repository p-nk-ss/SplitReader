package com.example.splitreader.data.translator.api

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Lightweight online translation endpoint. Returns a raw JSON array (parsed by
 * [com.example.splitreader.data.translator.QuickTranslateProvider]); no API key or setup required.
 */
interface QuickTranslateApi {
    @GET("translate_a/single")
    suspend fun translate(
        @Query("client") client: String = "gtx",
        @Query("sl") sourceLang: String,
        @Query("tl") targetLang: String,
        @Query("dt") dt: String = "t",
        @Query("q") text: String,
    ): String
}
