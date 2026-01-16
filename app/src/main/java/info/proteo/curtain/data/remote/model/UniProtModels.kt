package info.proteo.curtain.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * UniProt API search response wrapper.
 */
data class UniProtSearchResponse(
    @SerializedName("results")
    val results: List<UniProtEntry>
)

/**
 * UniProt protein entry data transfer object.
 * Matches UniProt REST API JSON structure.
 */
data class UniProtEntry(
    @SerializedName("primaryAccession")
    val primaryAccession: String,

    @SerializedName("uniProtkbId")
    val uniProtkbId: String,

    @SerializedName("entryType")
    val entryType: String?,

    @SerializedName("organism")
    val organism: OrganismDto?,

    @SerializedName("genes")
    val genes: List<GeneDto>?,

    @SerializedName("proteinDescription")
    val proteinDescription: ProteinDescriptionDto?,

    @SerializedName("comments")
    val comments: List<CommentDto>?,

    @SerializedName("sequence")
    val sequence: SequenceDto?
)

/**
 * Organism information from UniProt.
 */
data class OrganismDto(
    @SerializedName("scientificName")
    val scientificName: String,

    @SerializedName("commonName")
    val commonName: String?,

    @SerializedName("taxonId")
    val taxonId: Int
)

/**
 * Gene information from UniProt.
 */
data class GeneDto(
    @SerializedName("geneName")
    val geneName: GeneNameDto?,

    @SerializedName("synonyms")
    val synonyms: List<GeneNameDto>?
)

/**
 * Gene name value object.
 */
data class GeneNameDto(
    @SerializedName("value")
    val value: String
)

/**
 * Protein description from UniProt.
 */
data class ProteinDescriptionDto(
    @SerializedName("recommendedName")
    val recommendedName: ProteinNameDto?,

    @SerializedName("alternativeNames")
    val alternativeNames: List<ProteinNameDto>?,

    @SerializedName("submissionNames")
    val submissionNames: List<ProteinNameDto>?
)

/**
 * Protein name value object.
 */
data class ProteinNameDto(
    @SerializedName("fullName")
    val fullName: FullNameDto?,

    @SerializedName("shortNames")
    val shortNames: List<FullNameDto>?
)

/**
 * Full name value object.
 */
data class FullNameDto(
    @SerializedName("value")
    val value: String
)

/**
 * Comment from UniProt entry.
 */
data class CommentDto(
    @SerializedName("commentType")
    val commentType: String,

    @SerializedName("texts")
    val texts: List<TextDto>?
)

/**
 * Text value object.
 */
data class TextDto(
    @SerializedName("value")
    val value: String
)

/**
 * Sequence information from UniProt.
 */
data class SequenceDto(
    @SerializedName("value")
    val value: String,

    @SerializedName("length")
    val length: Int,

    @SerializedName("molWeight")
    val molWeight: Int?
)
