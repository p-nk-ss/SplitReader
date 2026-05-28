package com.example.splitreader.data.translator.api

import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleWebApi {
    @GET("translate_a/single")
    suspend fun translate(
        @Query("client") client: String = "gtx",
        @Query("sl") sourceLang: String,
        @Query("tl") targetLang: String,
        @Query("dt") dt: String = "t",
        @Query("q") text: String,
    ): String
}
