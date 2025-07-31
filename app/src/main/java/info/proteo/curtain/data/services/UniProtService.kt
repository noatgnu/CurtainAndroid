package info.proteo.curtain.data.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service for fetching protein information from UniProt database
 */
object UniProtService {
    
    private const val TAG = "UniProtService"
    private const val UNIPROT_API_BASE = "https://rest.uniprot.org"
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    data class ProteinInfo(
        val id: String,
        val geneName: String?,
        val proteinName: String?,
        val organism: String?,
        val function: String?
    )
    
    /**
     * Fetch protein information from UniProt
     * @param proteinId The protein ID to look up
     * @return ProteinInfo if found, null otherwise
     */
    suspend fun getProteinInfo(proteinId: String): ProteinInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching UniProt info for: $proteinId")
            
            // Try different ID formats that might be in the data
            val searchQueries = listOf(
                proteinId, // Direct ID
                proteinId.substringBefore("_"), // Remove isoform suffix if present
                proteinId.substringBefore("-"), // Remove variant suffix if present
            ).distinct()
            
            for (query in searchQueries) {
                val result = fetchFromUniProt(query)
                if (result != null) {
                    Log.d(TAG, "Found UniProt info for $proteinId using query: $query")
                    return@withContext result
                }
            }
            
            Log.w(TAG, "No UniProt info found for: $proteinId")
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching UniProt info for $proteinId", e)
            null
        }
    }
    
    private suspend fun fetchFromUniProt(proteinId: String): ProteinInfo? {
        try {
            // Use UniProt REST API to search for the protein
            val url = "$UNIPROT_API_BASE/uniprotkb/search?query=accession:$proteinId&format=json&size=1"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.w(TAG, "UniProt API request failed with code: ${response.code}")
                return null
            }
            
            val jsonResponse = response.body?.string() ?: return null
            val jsonObject = JSONObject(jsonResponse)
            
            val results = jsonObject.optJSONArray("results")
            if (results == null || results.length() == 0) {
                return null
            }
            
            val protein = results.getJSONObject(0)
            
            // Extract protein information
            val primaryAccession = protein.optString("primaryAccession", proteinId)
            
            // Get gene names
            val genes = protein.optJSONArray("genes")
            val geneName = if (genes != null && genes.length() > 0) {
                val gene = genes.getJSONObject(0)
                gene.optJSONObject("geneName")?.optString("value")
            } else null
            
            // Get protein names
            val proteinDescription = protein.optJSONObject("proteinDescription")
            val recommendedName = proteinDescription?.optJSONObject("recommendedName")
            val proteinName = recommendedName?.optJSONObject("fullName")?.optString("value")
            
            // Get organism
            val organism = protein.optJSONObject("organism")?.optString("scientificName")
            
            // Get function (simplified - could be more comprehensive)
            val comments = protein.optJSONArray("comments")
            var function: String? = null
            if (comments != null) {
                for (i in 0 until comments.length()) {
                    val comment = comments.getJSONObject(i)
                    if (comment.optString("commentType") == "FUNCTION") {
                        val texts = comment.optJSONArray("texts")
                        if (texts != null && texts.length() > 0) {
                            function = texts.getJSONObject(0).optString("value")
                            break
                        }
                    }
                }
            }
            
            return ProteinInfo(
                id = primaryAccession,
                geneName = geneName,
                proteinName = proteinName,
                organism = organism,
                function = function
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing UniProt response for $proteinId", e)
            return null
        }
    }
}