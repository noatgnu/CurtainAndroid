package info.proteo.curtain

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface CurtainApi {
    @GET("curtain/")
    suspend fun getAllCurtains(): Response<List<Curtain>>

    @GET("curtain/{link_id}/")
    suspend fun getCurtainByLinkId(@Path("link_id") linkId: String): Response<Curtain>

    @Multipart
    @POST("curtain/")
    suspend fun createCurtain(
        @Part file: MultipartBody.Part,
        @Part("description") description: RequestBody,
        @Part("curtain_type") curtainType: RequestBody,
        @Part("enable") enable: RequestBody,
        @Part("encrypted") encrypted: RequestBody,
        @Part("permanent") permanent: RequestBody
    ): Response<Curtain>

    @Multipart
    @PUT("curtain/{link_id}/")
    suspend fun updateCurtainWithFile(
        @Path("link_id") linkId: String,
        @Part file: MultipartBody.Part,
        @Part("description") description: RequestBody,
        @Part("curtain_type") curtainType: RequestBody,
        @Part("enable") enable: RequestBody,
        @Part("encrypted") encrypted: RequestBody,
        @Part("permanent") permanent: RequestBody
    ): Response<Curtain>

    @PUT("curtain/{link_id}/")
    suspend fun updateCurtainWithoutFile(
        @Path("link_id") linkId: String,
        @Body curtain: CurtainUpdateRequest
    ): Response<Curtain>

    @DELETE("curtain/{link_id}/")
    suspend fun deleteCurtain(@Path("link_id") linkId: String): Response<Unit>

    @GET("curtain/{url}/")
    suspend fun downloadCurtain(
        @Path("url", encoded = true) url: String,
    ): Response<okhttp3.ResponseBody>
}

@JsonClass(generateAdapter = true)
data class CurtainUpdateRequest(
    val description: String,
    @Json(name = "curtain_type")
    val curtainType: String,
    val enable: Boolean,
    val encrypted: Boolean,
    val permanent: Boolean
)
