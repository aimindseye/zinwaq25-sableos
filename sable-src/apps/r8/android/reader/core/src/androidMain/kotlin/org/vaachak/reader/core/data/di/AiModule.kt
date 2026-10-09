package org.vaachak.reader.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.vaachak.reader.core.data.remote.CloudflareAiApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            // High timeout for AI generation (30s)
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideCloudflareAiApi(client: OkHttpClient): CloudflareAiApi {
        return Retrofit.Builder()
            // 1. PLACEHOLDER URL: This satisfies Retrofit but is ignored
            // because we use @Url in the API call.
            .baseUrl("https://structure.placeholder/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudflareAiApi::class.java)
    }
}