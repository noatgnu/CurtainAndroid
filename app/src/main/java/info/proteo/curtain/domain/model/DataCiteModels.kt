package info.proteo.curtain.domain.model

import com.google.gson.annotations.SerializedName

data class DataCiteMetadata(
    @SerializedName("data")
    val data: DataCiteMetadataData
)

data class DataCiteMetadataData(
    @SerializedName("id")
    val id: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("attributes")
    val attributes: DataCiteMetadataAttributes
)

data class DataCiteMetadataAttributes(
    @SerializedName("doi")
    val doi: String,
    @SerializedName("prefix")
    val prefix: String,
    @SerializedName("suffix")
    val suffix: String,
    @SerializedName("identifiers")
    val identifiers: List<String>? = null,
    @SerializedName("alternateIdentifiers")
    val alternateIdentifiers: List<AlternateIdentifier>,
    @SerializedName("creators")
    val creators: List<DataCiteCreator>,
    @SerializedName("titles")
    val titles: List<DataCiteTitle>,
    @SerializedName("publisher")
    val publisher: String? = null,
    @SerializedName("publicationYear")
    val publicationYear: Int? = null,
    @SerializedName("descriptions")
    val descriptions: List<DataCiteDescription>? = null,
    @SerializedName("url")
    val url: String? = null,
    @SerializedName("state")
    val state: String? = null,
    @SerializedName("created")
    val created: String? = null,
    @SerializedName("updated")
    val updated: String? = null
)

data class AlternateIdentifier(
    @SerializedName("alternateIdentifier")
    val alternateIdentifier: String,
    @SerializedName("alternateIdentifierType")
    val alternateIdentifierType: String
)

data class DataCiteCreator(
    @SerializedName("name")
    val name: String,
    @SerializedName("affiliation")
    val affiliation: List<String>? = null,
    @SerializedName("nameIdentifiers")
    val nameIdentifiers: List<String>? = null
)

data class DataCiteTitle(
    @SerializedName("title")
    val title: String
)

data class DataCiteDescription(
    @SerializedName("description")
    val description: String,
    @SerializedName("descriptionType")
    val descriptionType: String? = null
)

data class DOIParsedData(
    val mainSessionUrl: String?,
    val collectionMetadata: DOICollectionMetadata?
)

data class DOICollectionMetadata(
    val title: String?,
    val description: String?,
    val allSessionLinks: List<DOISessionLink>
)

data class DOISessionLink(
    val sessionId: String,
    val sessionUrl: String,
    val title: String?
)

sealed class DOIError : Exception() {
    object InvalidDOI : DOIError() {
        override val message: String = "Invalid DOI format"
    }

    object MetadataFetchFailed : DOIError() {
        override val message: String = "Failed to fetch DOI metadata"
    }

    object NoAlternateIdentifiers : DOIError() {
        override val message: String = "No alternate identifiers found in DOI"
    }

    object SessionDataFetchFailed : DOIError() {
        override val message: String = "Failed to fetch session data from DOI"
    }

    object InvalidURL : DOIError() {
        override val message: String = "Invalid URL in alternate identifier"
    }
}
