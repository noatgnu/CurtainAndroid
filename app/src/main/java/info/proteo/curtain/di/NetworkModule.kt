package info.proteo.curtain.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.proteo.curtain.data.remote.api.CurtainApiService
import info.proteo.curtain.data.remote.api.UniProtApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module providing network dependencies.
 * Configures Retrofit, OkHttp, and API service instances.
 *
 * Provides multiple Retrofit instances for different backend hosts:
 * - @Named("curtain") - Default Curtain backend (configurable per site)
 * - @Named("uniprot") - UniProt REST API
 *
 * Matches iOS MultiHostNetworkManager architecture.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Default Curtain backend base URL.
     * Can be overridden dynamically for multi-site support.
     */
    private const val CURTAIN_BASE_URL = "https://celsus.muttsu.xyz/"

    /**
     * UniProt REST API base URL.
     */
    private const val UNIPROT_BASE_URL = "https://rest.uniprot.org/"

    /**
     * Provides base OkHttpClient with logging and timeout configuration only.
     * Used for external APIs (UniProt, presigned URLs, etc.) that should not
     * have backend-specific headers.
     *
     * Matches iOS URLSession configuration:
     * - 30s connect timeout
     * - 60s read timeout
     * - 60s write timeout
     *
     * @return Clean OkHttpClient without custom headers
     */
    @Provides
    @Singleton
    @Named("base")
    fun provideBaseOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Provides OkHttpClient for Curtain backend API with custom headers.
     * Adds Accept and Content-Type headers for backend requests.
     *
     * @param baseClient Base HTTP client
     * @return OkHttpClient configured for Curtain backend
     */
    @Provides
    @Singleton
    @Named("backend")
    fun provideBackendOkHttpClient(@Named("base") baseClient: OkHttpClient): OkHttpClient {
        return baseClient.newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    /**
     * Provides OkHttpClient for dynamic host creation and repository use.
     * This is the client that will be injected into repositories.
     *
     * @param baseClient Base HTTP client without custom headers
     * @return OkHttpClient for general use
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(@Named("base") baseClient: OkHttpClient): OkHttpClient {
        return baseClient
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .create()
    }

    /**
     * Provides Retrofit instance for Curtain backend API.
     * Uses backend-specific client with Accept and Content-Type headers.
     * Uses @Named qualifier for multi-instance support.
     *
     * @param backendClient Backend OkHttp client with custom headers
     * @return Retrofit instance configured for Curtain API
     */
    @Provides
    @Singleton
    @Named("curtain")
    fun provideCurtainRetrofit(@Named("backend") backendClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(CURTAIN_BASE_URL)
            .client(backendClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /**
     * Provides CurtainApiService instance.
     *
     * @param retrofit Curtain Retrofit instance
     * @return CurtainApiService implementation
     */
    @Provides
    @Singleton
    fun provideCurtainApiService(
        @Named("curtain") retrofit: Retrofit
    ): CurtainApiService {
        return retrofit.create(CurtainApiService::class.java)
    }

    /**
     * Provides Retrofit instance for UniProt REST API.
     * Uses @Named qualifier for multi-instance support.
     *
     * @param okHttpClient Shared OkHttp client
     * @return Retrofit instance configured for UniProt API
     */
    @Provides
    @Singleton
    @Named("uniprot")
    fun provideUniProtRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(UNIPROT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /**
     * Provides UniProtApiService instance.
     *
     * @param retrofit UniProt Retrofit instance
     * @return UniProtApiService implementation
     */
    @Provides
    @Singleton
    fun provideUniProtApiService(
        @Named("uniprot") retrofit: Retrofit
    ): UniProtApiService {
        return retrofit.create(UniProtApiService::class.java)
    }

    /**
     * Creates a dynamic Retrofit instance for a specific Curtain backend hostname.
     * Used for multi-site support when switching between backend servers.
     * Adds backend-specific headers (Accept, Content-Type) to the provided client.
     * Handles hostnames with or without /api/ path.
     *
     * @param hostname Backend server hostname (e.g., "https://celsus.muttsu.xyz/" or "https://celsus.muttsu.xyz/")
     * @param okHttpClient Base OkHttp client (will be augmented with backend headers)
     * @return Retrofit instance for the specified host with backend headers
     */
    fun createRetrofitForHost(
        hostname: String,
        okHttpClient: OkHttpClient
    ): Retrofit {
        val baseUrl = if (hostname.startsWith("http")) {
            if (!hostname.endsWith("/")) "$hostname/" else hostname
        } else {
            "https://$hostname/"
        }

        val backendClient = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(backendClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
