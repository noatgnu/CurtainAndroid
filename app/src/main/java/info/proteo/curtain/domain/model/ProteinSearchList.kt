package info.proteo.curtain.domain.model

data class ProteinSearchList(
    val id: String,
    val curtainLinkId: String,
    val name: String,
    val proteinIds: List<String>,
    val description: String = "",
    val createdAt: Long,
    val modifiedAt: Long
)

data class SearchResult(
    val proteinId: String,
    val geneName: String?,
    val log2FC: Double?,
    val pValue: Double?,
    val isSignificant: Boolean,
    val matchType: SearchMatchType
)

enum class SearchMatchType {
    EXACT_PROTEIN_ID,
    EXACT_GENE_NAME,
    CONTAINS_PROTEIN_ID,
    CONTAINS_GENE_NAME,
    REGEX_MATCH
}

data class SearchQuery(
    val query: String,
    val searchInProteinIds: Boolean = true,
    val searchInGeneNames: Boolean = true,
    val caseSensitive: Boolean = false,
    val useRegex: Boolean = false,
    val exactMatch: Boolean = false
)
