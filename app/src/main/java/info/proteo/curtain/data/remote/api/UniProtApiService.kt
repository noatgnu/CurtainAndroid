package info.proteo.curtain.data.remote.api

import info.proteo.curtain.data.remote.model.UniProtSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service interface for UniProt REST API.
 * Provides protein annotation and search functionality.
 *
 * Base URL: https://rest.uniprot.org/
 * Matches iOS UniProtService implementation.
 */
interface UniProtApiService {

    /**
     * Search proteins in UniProt database.
     * Matches iOS: GET /uniprotkb/search
     *
     * Query syntax examples:
     * - Single accession: "accession:P12345"
     * - Multiple accessions: "accession:P12345 OR accession:Q67890"
     * - Gene name: "gene:BRCA1"
     * - Organism: "organism_id:9606" (human)
     *
     * @param query Lucene query string
     * @param format Response format (always "json")
     * @param fields Comma-separated list of fields to return (empty for all)
     * @param size Maximum number of results (default 500, max 500)
     * @return Search response with protein entries
     */
    @GET("uniprotkb/search")
    suspend fun searchProteins(
        @Query("query") query: String,
        @Query("format") format: String = "json",
        @Query("fields") fields: String = "",
        @Query("size") size: Int = 500
    ): UniProtSearchResponse

    /**
     * Get protein entries by accession numbers.
     * Constructs query for multiple accessions.
     *
     * @param accessions List of UniProt accession IDs
     * @param size Maximum number of results
     * @return Search response with protein entries
     */
    suspend fun getProteinsByAccessions(
        accessions: List<String>,
        size: Int = 500
    ): UniProtSearchResponse {
        val query = accessions.joinToString(" OR ") { "accession:$it" }
        return searchProteins(query = query, size = size)
    }

    /**
     * Search proteins by gene names.
     *
     * @param geneNames List of gene names
     * @param size Maximum number of results
     * @return Search response with protein entries
     */
    suspend fun getProteinsByGeneNames(
        geneNames: List<String>,
        size: Int = 500
    ): UniProtSearchResponse {
        val query = geneNames.joinToString(" OR ") { "gene:$it" }
        return searchProteins(query = query, size = size)
    }

    /**
     * Search proteins by organism.
     *
     * @param organismId NCBI taxonomy ID (e.g., 9606 for human)
     * @param query Additional query parameters
     * @param size Maximum number of results
     * @return Search response with protein entries
     */
    suspend fun getProteinsByOrganism(
        organismId: Int,
        query: String = "",
        size: Int = 500
    ): UniProtSearchResponse {
        val fullQuery = if (query.isNotEmpty()) {
            "organism_id:$organismId AND ($query)"
        } else {
            "organism_id:$organismId"
        }
        return searchProteins(query = fullQuery, size = size)
    }
}
