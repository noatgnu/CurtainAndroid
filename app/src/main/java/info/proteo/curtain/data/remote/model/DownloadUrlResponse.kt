package info.proteo.curtain.data.remote.model

import com.google.gson.annotations.SerializedName

data class DownloadUrlResponse(
    @SerializedName("url")
    val url: String
)
