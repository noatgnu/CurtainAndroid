package info.proteo.curtain.data.services

import android.util.Log
import info.proteo.curtain.AppData
import info.proteo.curtain.data.models.*
import info.proteo.curtain.DataFilterListRepository
import info.proteo.curtain.DataFilterList
import info.proteo.curtain.DataFilterListRequest
import info.proteo.curtain.UniprotService
import info.proteo.curtain.CurtainDataService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinx.dataframe.api.count
import org.jetbrains.kotlinx.dataframe.api.forEach
import org.jetbrains.kotlinx.dataframe.api.getColumn
import org.jetbrains.kotlinx.dataframe.size
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchService @Inject constructor(
    private val dataFilterListRepository: DataFilterListRepository,
) {
    private val _searchSession = MutableStateFlow(SearchSession())
    val searchSession: StateFlow<SearchSession> = _searchSession.asStateFlow()
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    // Keep reference to current curtain data for auto-saving
    private var currentCurtainData: AppData? = null
    
    var uniprotService: UniprotService? = null
    var curtainDataService: CurtainDataService? = null

    /**
     * Initialize SearchService with services from CurtainDetailsViewModel
     */
    fun initializeWithViewModelServices(
        uniprotService: UniprotService,
        curtainDataService: CurtainDataService
    ) {
        this.uniprotService = uniprotService
        this.curtainDataService = curtainDataService
    }

    suspend fun performTypeaheadSearch(
        query: String,
        searchType: SearchType,
        curtainData: AppData,
        limit: Int = 10
    ): List<TypeaheadSuggestion> = withContext(Dispatchers.IO) {
        Log.d("SearchService", "${uniprotService?.geneNameToAcc}")
        if (query.length < 2) return@withContext emptyList()
        
        // Replicate frontend logic: v.toLowerCase().indexOf(term.toLowerCase()) > -1
        val queryLower = query.lowercase()
        val suggestions = mutableListOf<TypeaheadSuggestion>()
        when (searchType) {
            SearchType.PRIMARY_ID -> {
                // Replicate: return this.primaryIDsList.filter(v => v.toLowerCase().indexOf(term.toLowerCase()) > -1).slice(0,10)
                curtainData.raw.df.getColumn(curtainData.rawForm.primaryIDs).forEach { proteinId ->
                    val proteinIdLower = proteinId.toString().lowercase()
                    val splittedIds = proteinIdLower.split(";")
                    splittedIds.forEach { splittedId ->
                        if (splittedId.indexOf(queryLower) > -1) {
                            suggestions.add(TypeaheadSuggestion(splittedId.uppercase(), SearchType.PRIMARY_ID))
                            return@forEach
                        }
                    }
                }
            }
            
            SearchType.GENE_NAME -> {
                curtainData.allGenes.forEach { geneName ->
                    val geneNameLower = geneName.lowercase()
                    val splittedNames = geneNameLower.split(";")
                    splittedNames.forEach { splittedName ->
                        if (splittedName.indexOf(queryLower) > -1) {
                            suggestions.add(TypeaheadSuggestion(geneName.uppercase(), SearchType.GENE_NAME))
                            return@forEach
                        }
                    }
                }
            }
            
            SearchType.ACCESSION_ID -> {

                uniprotService?.accMap?.keys?.forEach { accessionId ->
                    val accessionIdLower = accessionId.lowercase()
                    val splittedAccessionIds = accessionIdLower.split(";")
                    splittedAccessionIds.forEach { splittedAccessionId ->
                        if (splittedAccessionId.indexOf(queryLower) > -1) {
                            suggestions.add(TypeaheadSuggestion(splittedAccessionId.uppercase(), SearchType.ACCESSION_ID))
                            return@forEach
                        }
                    }
                }
            }
        }

        return@withContext suggestions.take(limit)
    }
    
    /**
     * Performs single term search
     */
    suspend fun performSingleSearch(
        searchTerm: String,
        searchType: SearchType,
        curtainData: AppData,
        createList: Boolean = true,
        listName: String? = null
    ): Pair<List<SearchResult>, SearchStatistics> = withContext(Dispatchers.IO) {
        _isSearching.value = true
        val startTime = System.currentTimeMillis()

        try {

            val results = searchProteinData(listOf(searchTerm), searchType, curtainData)
            val statistics = SearchStatistics(
                totalProteins = curtainData.raw.df.count() ?: 0,
                matchedProteins = results.size,
                unmatchedTerms = if (results.isEmpty()) listOf(searchTerm) else emptyList(),
                exactMatches = results.count { it.matchType == MatchType.EXACT },
                partialMatches = results.count { it.matchType == MatchType.PARTIAL },
                searchDurationMs = System.currentTimeMillis() - startTime
            )
            
            if (createList && results.isNotEmpty()) {
                val finalListName = listName ?: "Search: $searchTerm"
                createSearchList(finalListName, results.map { it.proteinId }, listOf(searchTerm), searchType)
            }
            
            Pair(results, statistics)
        } finally {
            _isSearching.value = false
        }
    }
    
    /**
     * Performs batch search based on the frontend implementation
     * Replicates the exact string processing logic from batch-search.component.ts
     */
    suspend fun performBatchSearch(
        request: BatchSearchRequest,
        curtainData: AppData
    ): Pair<List<SearchResult>, SearchStatistics> = withContext(Dispatchers.IO) {
        _isSearching.value = true
        val startTime = System.currentTimeMillis()
        
        try {
            // Process the search terms using the exact frontend logic
            val processedTerms = processBatchSearchInput(request.searchTerms)
            val results = searchProteinDataWithExactMatching(processedTerms, request.searchType, curtainData)
            
            val matchedTerms = results.map { it.searchTerm }.toSet()
            val allInputTerms = processedTerms.values.flatten()
            val unmatchedTerms = allInputTerms.filter { it !in matchedTerms }
            
            val statistics = SearchStatistics(
                totalProteins = curtainData.allGenes?.size ?: 0,
                matchedProteins = results.map { it.proteinId }.distinct().size,
                unmatchedTerms = unmatchedTerms,
                exactMatches = results.count { it.matchType == MatchType.EXACT },
                partialMatches = results.count { it.matchType == MatchType.PARTIAL },
                searchDurationMs = System.currentTimeMillis() - startTime
            )
            
            if (results.isNotEmpty()) {
                val existingList = _searchSession.value.searchLists.find { it.name == request.listName }
                
                if (existingList != null && request.overwriteExisting) {
                    removeSearchList(existingList.id)
                }
                
                createSearchList(
                    name = request.listName,
                    proteinIds = results.map { it.proteinId }.distinct(),
                    searchTerms = allInputTerms,
                    searchType = request.searchType,
                    color = request.color
                )
            }
            
            Pair(results, statistics)
        } finally {
            _isSearching.value = false
        }
    }
    
    /**
     * Processes batch search input exactly like the frontend
     * Based on batch-search.component.ts handleSubmit() method
     */
    private fun processBatchSearchInput(inputLines: List<String>): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        
        // Join all input lines and process as single string (like frontend textarea)
        val inputText = inputLines.joinToString("\n")
        
        // Replicate frontend logic: for (const r of this.data.replace("\r", "").split("\n"))
        val lines = inputText.replace("\r", "").split("\n")
        
        for (line in lines) {
            // const a = r.trim().toUpperCase()
            val processedLine = line.trim().uppercase()
            
            // if (a !== "")
            if (processedLine.isNotEmpty()) {
                // const e = a.split(";")
                val semicolonSplit = processedLine.split(";")
                
                // if (!result[a]) { result[a] = [] }
                if (!result.containsKey(processedLine)) {
                    result[processedLine] = mutableListOf()
                }
                
                // for (let f of e) { f = f.trim(); result[a].push(f) }
                for (subTerm in semicolonSplit) {
                    val trimmedSubTerm = subTerm.trim()
                    if (trimmedSubTerm.isNotEmpty()) {
                        result[processedLine]!!.add(trimmedSubTerm)
                    }
                }
            }
        }
        
        return result
    }
    
    /**
     * Search with exact matching logic replicating frontend parseData() method
     * Based on protein-selections.component.ts getPrimaryIDsDataFromBatch() and parseData()
     */
    private suspend fun searchProteinDataWithExactMatching(
        processedTerms: Map<String, List<String>>,
        searchType: SearchType,
        curtainData: AppData
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        Log.d("SearchService", "Searching with exact matching for terms: $processedTerms, type: $searchType")
        val results = mutableListOf<SearchResult>()
        for ((originalLine, subTerms) in processedTerms) {
            val exactResults = parseDataExact(originalLine, searchType, curtainData)
            
            if (exactResults.isNotEmpty()) {
                results.addAll(exactResults)
            } else {
                for (subTerm in subTerms) {
                    val fuzzyResults = parseDataFuzzy(subTerm, searchType, curtainData)
                    if (fuzzyResults.isNotEmpty()) {
                        results.addAll(fuzzyResults)
                        break
                    }
                }
            }
        }
        
        return@withContext results.distinctBy { it.proteinId }
    }
    
    /**
     * Exact search matching replicating frontend parseData(data, d, true)
     */
    private suspend fun parseDataExact(
        searchTerm: String,
        searchType: SearchType,
        curtainData: AppData
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResult>()
        
        when (searchType) {
            SearchType.GENE_NAME -> {
                // Replicate: return this.data.getPrimaryIDsFromGeneNames(d)
                val proteinIds = getPrimaryIDsFromGeneNames(searchTerm, curtainData)
                proteinIds.forEach { proteinId ->
                    val geneName = uniprotService?.getUniprotFromPrimary(proteinId)?.get("Gene Names")?.toString()
                    results.add(SearchResult(
                        proteinId = proteinId,
                        geneName = geneName,
                        searchType = SearchType.GENE_NAME,
                        searchTerm = searchTerm,
                        matchType = MatchType.EXACT
                    ))
                }
            }
            
            SearchType.PRIMARY_ID -> {
                // Replicate: return this.data.getPrimaryIDsFromAcc(d)
                val proteinIds = getPrimaryIDsFromPrimaryId(searchTerm, curtainData)
                proteinIds.forEach { proteinId ->
                    val geneName = uniprotService?.getUniprotFromPrimary(proteinId)?.get("Gene Names")?.toString()
                    results.add(SearchResult(
                        proteinId = proteinId,
                        geneName = geneName,
                        searchType = SearchType.PRIMARY_ID,
                        searchTerm = searchTerm,
                        matchType = MatchType.EXACT
                    ))
                }
            }
            
            SearchType.ACCESSION_ID -> {
                // Search by accession ID -> primary IDs via accMap
                val primaryIds = uniprotService?.accMap?.get(searchTerm)
                if (primaryIds != null) {
                    (primaryIds as? List<*>)?.forEach { primaryId ->
                        val proteinId = primaryId.toString()
                        val geneName = uniprotService?.getUniprotFromPrimary(proteinId)?.get("Gene Names")?.toString()
                        results.add(SearchResult(
                            proteinId = proteinId,
                            geneName = geneName,
                            accessionId = searchTerm,
                            searchType = SearchType.ACCESSION_ID,
                            searchTerm = searchTerm,
                            matchType = MatchType.EXACT
                        ))
                    }
                }
            }
        }
        
        return@withContext results
    }
    
    /**
     * Fuzzy search matching replicating frontend parseData(data, dd, false)
     */
    private suspend fun parseDataFuzzy(
        searchTerm: String,
        searchType: SearchType,
        curtainData: AppData
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResult>()
        
        when (searchType) {
            SearchType.GENE_NAME -> {
                // Replicate: if (this.data.genesMap[d]) { for (const m in this.data.genesMap[d]) { ... } }
                val genesMap = curtainData.genesMap as? Map<String, Any>
                genesMap?.get(searchTerm)?.let { mappedData ->
                    if (mappedData is Map<*, *>) {
                        for (mappedKey in mappedData.keys) {
                            val proteinIds = getPrimaryIDsFromGeneNames(mappedKey.toString(), curtainData)
                            if (proteinIds.isNotEmpty()) {
                                proteinIds.forEach { proteinId ->
                                    val geneName = uniprotService?.getUniprotFromPrimary(proteinId)?.get("Gene Names")?.toString()
                                    results.add(SearchResult(
                                        proteinId = proteinId,
                                        geneName = geneName,
                                        searchType = SearchType.GENE_NAME,
                                        searchTerm = searchTerm,
                                        matchType = MatchType.FUZZY
                                    ))
                                }
                                break // Stop at first successful match
                            }
                        }
                    }
                }
            }
            
            SearchType.PRIMARY_ID -> {
                // Replicate: if (this.data.primaryIDsMap[d]) { for (const m in this.data.primaryIDsMap[d]) { ... } }
                val primaryIDsMap = curtainData.primaryIDsMap as? Map<String, Any>
                primaryIDsMap?.get(searchTerm)?.let { mappedData ->
                    if (mappedData is Map<*, *>) {
                        for (mappedKey in mappedData.keys) {
                            val proteinIds = getPrimaryIDsFromPrimaryId(mappedKey.toString(), curtainData)
                            if (proteinIds.isNotEmpty()) {
                                proteinIds.forEach { proteinId ->
                                    val geneName = uniprotService?.getUniprotFromPrimary(proteinId)?.get("Gene Names")?.toString()
                                    results.add(SearchResult(
                                        proteinId = proteinId,
                                        geneName = geneName,
                                        searchType = SearchType.PRIMARY_ID,
                                        searchTerm = searchTerm,
                                        matchType = MatchType.FUZZY
                                    ))
                                }
                                break
                            }
                        }
                    }
                }
            }
            
            SearchType.ACCESSION_ID -> {
                // For accession IDs, try partial matching -> convert to primary IDs
                uniprotService?.accMap?.forEach { (accessionId, primaryIdList) ->
                    if (accessionId.lowercase().contains(searchTerm.lowercase())) {
                        val accessionIdLower = accessionId.lowercase()
                        val splittedAccessionIds = accessionIdLower.split(";")
                        splittedAccessionIds.forEach { splittedAccessionId ->
                            if (splittedAccessionId == searchTerm.lowercase()) {
                                // Add all primary IDs associated with this accession ID
                                (primaryIdList as? List<*>)?.forEach { primaryId ->
                                    val proteinId = primaryId.toString()
                                    val geneName = uniprotService?.getUniprotFromPrimary(proteinId)?.get("Gene Names")?.toString()
                                    results.add(SearchResult(
                                        proteinId = proteinId,
                                        geneName = geneName,
                                        accessionId = accessionId,
                                        searchType = SearchType.ACCESSION_ID,
                                        searchTerm = searchTerm,
                                        matchType = MatchType.PARTIAL
                                    ))
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return@withContext results
    }
    
    /**
     * Get primary IDs from gene names - replicates frontend getPrimaryIDsFromGeneNames()
     * Chain: geneName -> geneNameToAcc -> accMap -> primaryIds
     */
    private fun getPrimaryIDsFromGeneNames(geneName: String, curtainData: AppData): List<String> {
        val results = mutableListOf<String>()
        val processedGeneName = geneName.uppercase().trim()
        
        Log.d("SearchService", "getPrimaryIDsFromGeneNames: Looking for gene name '$processedGeneName'")
        Log.d("SearchService", "geneNameToAcc available: ${uniprotService?.geneNameToAcc != null}")
        Log.d("SearchService", "geneNameToAcc size: ${uniprotService?.geneNameToAcc?.size}")
        Log.d("SearchService", "geneNameToAcc keys sample: ${uniprotService?.geneNameToAcc?.keys?.take(5)}")
        
        // First: gene name -> accession IDs via geneNameToAcc
        val accessionIds = uniprotService?.geneNameToAcc?.get(processedGeneName)
        Log.d("SearchService", "Found accessionIds for '$processedGeneName': $accessionIds")
        
        if (accessionIds != null) {
            // geneNameToAcc returns a Map with accession IDs as keys, not a List
            (accessionIds as? Map<*, *>)?.keys?.forEach { accessionId ->
                val accId = accessionId.toString()
                Log.d("SearchService", "Looking up accession ID '$accId' in accMap")
                val primaryIds = uniprotService?.accMap?.get(accId)
                Log.d("SearchService", "Found primaryIds for '$accId': $primaryIds")
                
                if (primaryIds != null) {
                    (primaryIds as? List<*>)?.forEach { primaryId ->
                        results.add(primaryId.toString())
                        Log.d("SearchService", "Added primary ID: ${primaryId.toString()}")
                    }
                }
            }
        } else {
            Log.d("SearchService", "No accession IDs found for gene name '$processedGeneName'")
            // Try alternative lookup - check if gene name exists in any form
            uniprotService?.geneNameToAcc?.keys?.forEach { key ->
                if (key.contains(processedGeneName, ignoreCase = true)) {
                    Log.d("SearchService", "Found similar gene name key: '$key'")
                }
            }
        }
        
        Log.d("SearchService", "Final results for gene '$processedGeneName': $results")
        return results.distinct()
    }
    
    /**
     * Get primary IDs from primary ID search - replicates frontend getPrimaryIDsFromAcc()
     */
    private fun getPrimaryIDsFromPrimaryId(primaryId: String, curtainData: AppData): List<String> {
        val results = mutableListOf<String>()

        curtainData.raw.df.getColumn(curtainData.rawForm.primaryIDs).forEach { proteinId ->
            if (proteinId.toString().uppercase() == primaryId.uppercase()) {
                results.add(proteinId.toString())
            }
        }
        
        return results
    }
    
    /**
     * Core search logic that handles the actual protein data searching (legacy method)
     */
    private suspend fun searchProteinData(
        searchTerms: List<String>,
        searchType: SearchType,
        curtainData: AppData
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        // Convert to the new format and use the exact matching logic
        val processedTerms = mutableMapOf<String, List<String>>()
        Log.d("SearchService", "${uniprotService?.geneNameToAcc?.keys}")

        when (searchType) {
            SearchType.PRIMARY_ID -> {
                searchTerms.forEach { term ->
                    val processedTerm = term.trim().uppercase()
                    if (processedTerm.isNotEmpty()) {
                        processedTerms[processedTerm] = listOf(processedTerm)
                    }
                }
            }
            SearchType.GENE_NAME -> {
                Log.d("SearchService", "Processing gene names for search: $searchTerms")
                Log.d("SearchService", "geneNameToAcc available: ${uniprotService?.geneNameToAcc != null}, size: ${uniprotService?.geneNameToAcc?.size}")
                searchTerms.forEach { term ->
                    val processedTerm = term.trim().uppercase()
                    Log.d("SearchService", "Processing gene name term: '$processedTerm'")
                    if (processedTerm.isNotEmpty()) {
                        // Chain: geneName -> geneNameToAcc -> accMap -> primaryIds
                        val primaryIds = mutableListOf<String>()
                        val accessionIds = uniprotService?.geneNameToAcc?.get(processedTerm)
                        Log.d("SearchService", "Found accessionIds for '$processedTerm': $accessionIds")
                        
                        if (accessionIds != null) {
                            // geneNameToAcc returns a Map with accession IDs as keys, not a List
                            (accessionIds as? Map<*, *>)?.keys?.forEach { accessionId ->
                                val accId = accessionId.toString()
                                Log.d("SearchService", "Looking up accession ID '$accId'")
                                val primaryIdList = uniprotService?.accMap?.get(accId)
                                Log.d("SearchService", "Found primaryIdList for '$accId': $primaryIdList")
                                if (primaryIdList != null) {
                                    (primaryIdList as? List<*>)?.forEach { primaryId ->
                                        primaryIds.add(primaryId.toString())
                                    }
                                }
                            }
                        } else {
                            Log.d("SearchService", "No accessionIds found for gene name '$processedTerm'")
                        }
                        
                        if (primaryIds.isNotEmpty()) {
                            Log.d("SearchService", "Final primaryIds for '$processedTerm': $primaryIds")
                            processedTerms[processedTerm] = primaryIds.distinct()
                        } else {
                            Log.d("SearchService", "No primaryIds found, using original term")
                            processedTerms[processedTerm] = listOf(processedTerm)
                        }
                    }
                }
            }
            SearchType.ACCESSION_ID -> {
                // For accession IDs -> convert to primary IDs via accMap
                searchTerms.forEach { term ->
                    val processedTerm = term.trim().uppercase()
                    if (processedTerm.isNotEmpty()) {
                        val primaryIds = uniprotService?.accMap?.get(processedTerm)
                        if (primaryIds != null) {
                            val primaryIdList = (primaryIds as? List<*>)?.map { it.toString() } ?: emptyList()
                            processedTerms[processedTerm] = primaryIdList
                        } else {
                            processedTerms[processedTerm] = listOf(processedTerm)
                        }
                    }
                }
            }
        }
        return@withContext searchProteinDataWithExactMatching(processedTerms, searchType, curtainData)
    }
    
    /**
     * Creates a new search list
     */
    fun createSearchList(
        name: String,
        proteinIds: List<String>,
        searchTerms: List<String> = emptyList(),
        searchType: SearchType = SearchType.PRIMARY_ID,
        color: String? = null,
        description: String? = null
    ): SearchList {
        val currentSession = _searchSession.value
        val searchList = SearchList(
            id = UUID.randomUUID().toString(),
            name = name,
            color = color ?: currentSession.getNextAvailableColor(),
            proteinIds = proteinIds.distinct(),
            searchTerms = searchTerms,
            searchType = searchType,
            description = description
        )
        
        val updatedSession = currentSession.copy()
        updatedSession.addSearchList(searchList)
        _searchSession.value = updatedSession
        autoSaveSearchListsIfNeeded()
        
        return searchList
    }
    
    /**
     * Removes a search list
     */
    fun removeSearchList(listId: String) {
        val currentSession = _searchSession.value
        val updatedSession = currentSession.copy()
        updatedSession.removeSearchList(listId)
        _searchSession.value = updatedSession
        
        // Auto-save to CurtainDataService
        autoSaveSearchListsIfNeeded()
    }
    
    /**
     * Toggles a search list filter
     */
    fun toggleSearchListFilter(listId: String) {
        val currentSession = _searchSession.value
        val updatedFilters = currentSession.activeFilters.toMutableSet()
        
        if (listId in updatedFilters) {
            updatedFilters.remove(listId)
        } else {
            updatedFilters.add(listId)
        }
        
        _searchSession.value = currentSession.copy(
            activeFilters = updatedFilters
        )
    }
    
    /**
     * Sets multiple search list filters
     */
    fun setSearchListFilters(listIds: List<String>) {
        val currentSession = _searchSession.value
        _searchSession.value = currentSession.copy(
            activeFilters = listIds.toMutableSet()
        )
    }
    
    /**
     * Sets stored selection filters (for volcano plot selections, etc.)
     */
    fun setStoredSelectionFilters(selectionNames: List<String>) {
        val currentSession = _searchSession.value
        _searchSession.value = currentSession.copy(
            activeStoredSelections = selectionNames.toMutableSet()
        )
    }
    
    /**
     * Clears all search list filters
     */
    fun clearAllFilters() {
        val currentSession = _searchSession.value
        _searchSession.value = currentSession.copy(
            activeFilters = mutableSetOf(),
            activeStoredSelections = mutableSetOf()
        )
    }
    
    /**
     * Gets all search lists
     */
    fun getSearchLists(): List<SearchList> {
        return _searchSession.value.searchLists
    }
    
    /**
     * Gets active search lists (currently filtered)
     */
    fun getActiveSearchLists(): List<SearchList> {
        val currentSession = _searchSession.value
        return currentSession.searchLists.filter { it.id in currentSession.activeFilters }
    }
    
    /**
     * Gets filtered protein IDs based on active search lists and stored selections
     */
    fun getFilteredProteinIds(): List<String> {
        val storedSelections = getAllStoredSelections()
        return _searchSession.value.getFilteredProteins(storedSelections)
    }
    
    /**
     * Gets protein IDs for a specific stored operation (including non-search-list operations)
     * This allows filtering by volcano plot selections and other stored operations
     */
    fun getProteinIdsForOperation(operationName: String): List<String> {
        return getProteinsForStoredOperation(operationName)
    }
    
    /**
     * Checks if a protein ID is in any active search list
     */
    fun isProteinInActiveSearchLists(proteinId: String): Boolean {
        val filteredProteins = getFilteredProteinIds()
        return filteredProteins.contains(proteinId)
    }
    
    /**
     * Checks if a specific search list filter is active
     */
    fun isSearchListFilterActive(listId: String): Boolean {
        return _searchSession.value.activeFilters.contains(listId)
    }
    
    /**
     * Changes the color of a search list
     */
    fun changeSearchListColor(listId: String, newColor: String): Boolean {
        val currentSession = _searchSession.value
        val updatedLists = currentSession.searchLists.toMutableList()
        
        val listIndex = updatedLists.indexOfFirst { it.id == listId }
        if (listIndex != -1) {
            val updatedList = updatedLists[listIndex].copy(color = newColor)
            updatedLists[listIndex] = updatedList
            
            val updatedSession = currentSession.copy(searchLists = updatedLists)
            _searchSession.value = updatedSession
            return true
        }
        
        return false
    }
    
    /**
     * Renames a search list
     */
    fun renameSearchList(listId: String, newName: String): Boolean {
        val currentSession = _searchSession.value
        val updatedLists = currentSession.searchLists.toMutableList()
        
        val listIndex = updatedLists.indexOfFirst { it.id == listId }
        if (listIndex != -1) {
            val updatedList = updatedLists[listIndex].copy(name = newName)
            updatedLists[listIndex] = updatedList
            
            val updatedSession = currentSession.copy(searchLists = updatedLists)
            _searchSession.value = updatedSession
            return true
        }
        
        return false
    }
    
    /**
     * Gets a search list by ID
     */
    fun getSearchListById(listId: String): SearchList? {
        return _searchSession.value.searchLists.find { it.id == listId }
    }
    
    /**
     * Updates a search list
     */
    fun updateSearchList(listId: String, updatedList: SearchList): Boolean {
        val currentSession = _searchSession.value
        val updatedLists = currentSession.searchLists.toMutableList()
        
        val listIndex = updatedLists.indexOfFirst { it.id == listId }
        if (listIndex != -1) {
            updatedLists[listIndex] = updatedList.copy(id = listId)
            
            val updatedSession = currentSession.copy(searchLists = updatedLists)
            _searchSession.value = updatedSession
            return true
        }
        
        return false
    }
    
    /**
     * Gets all available filter lists from the local database only
     */
    suspend fun getAvailableFilterLists(): List<DataFilterList> = withContext(Dispatchers.IO) {
        val entities = dataFilterListRepository.getAllDataFilterLists()
        return@withContext entities.map { entity ->
            DataFilterList(
                id = entity.id,
                name = entity.name,
                data = entity.data,
                isDefault = entity.isDefault
            )
        }
    }
    
    /**
     * Gets filter lists grouped by category from local database only
     */
    suspend fun getFilterListsByCategory(): Map<String, List<DataFilterList>> = withContext(Dispatchers.IO) {
        val entities = dataFilterListRepository.getAllDataFilterLists()
        return@withContext entities.groupBy { it.category }.mapValues { (_, entityList) ->
            entityList.map { entity ->
                DataFilterList(
                    id = entity.id,
                    name = entity.name,
                    data = entity.data,
                    isDefault = entity.isDefault
                )
            }
        }
    }
    
    /**
     * Imports a filter list as a search list
     * Replicates the frontend logic for importing pre-defined protein lists
     */
    suspend fun importFilterList(
        request: FilterListImportRequest,
        curtainData: AppData
    ): Pair<SearchList?, SearchStatistics> = withContext(Dispatchers.IO) {
        _isSearching.value = true
        val startTime = System.currentTimeMillis()
        
        try {
            // Get the filter list data from local database only
            val filterListEntity = dataFilterListRepository.getDataFilterListById(request.filterListId)
                ?: return@withContext Pair(null, SearchStatistics(0, 0, emptyList(), 0, 0, 0))
            
            val filterList = DataFilterList(
                id = filterListEntity.id,
                name = filterListEntity.name,
                data = filterListEntity.data,
                isDefault = filterListEntity.isDefault
            )
            
            // Process the filter list data exactly like the frontend
            val filterData = processFilterListData(filterList.data)
            val validProteinIds = validateProteinIds(filterData, curtainData)
            
            val statistics = SearchStatistics(
                totalProteins = curtainData.allGenes?.size ?: 0,
                matchedProteins = validProteinIds.size,
                unmatchedTerms = filterData.filter { it !in validProteinIds },
                exactMatches = validProteinIds.size,
                partialMatches = 0,
                searchDurationMs = System.currentTimeMillis() - startTime
            )
            
            if (validProteinIds.isNotEmpty()) {
                val existingList = _searchSession.value.searchLists.find { 
                    it.name == (request.listName ?: filterList.name) 
                }
                
                if (existingList != null && request.overwriteExisting) {
                    removeSearchList(existingList.id)
                }
                
                val searchList = createSearchList(
                    name = request.listName ?: filterList.name,
                    proteinIds = validProteinIds,
                    searchTerms = emptyList(), // Filter lists don't have search terms
                    searchType = SearchType.PRIMARY_ID, // Default for filter lists
                    color = request.color,
                    description = "Imported from filter list: ${filterListEntity.category}"
                )
                
                return@withContext Pair(searchList, statistics)
            }
            
            return@withContext Pair(null, statistics)
        } finally {
            _isSearching.value = false
        }
    }
    
    /**
     * Processes filter list data exactly like the frontend
     * Based on the same logic as batch search input processing
     */
    private fun processFilterListData(filterData: String): List<String> {
        val results = mutableListOf<String>()
        
        // Replicate frontend filter list processing logic
        val lines = filterData.replace("\r", "").split("\n")
        
        for (line in lines) {
            val processedLine = line.trim().uppercase()
            if (processedLine.isNotEmpty()) {
                // Handle semicolon-delimited entries in filter lists
                val semicolonSplit = processedLine.split(";")
                for (subTerm in semicolonSplit) {
                    val trimmedSubTerm = subTerm.trim()
                    if (trimmedSubTerm.isNotEmpty()) {
                        results.add(trimmedSubTerm)
                    }
                }
            }
        }
        
        return results.distinct()
    }
    
    /**
     * Validates which protein IDs from filter list exist in the current dataset
     */
    private fun validateProteinIds(proteinIds: List<String>, curtainData: AppData): List<String> {
        val validIds = mutableListOf<String>()
        val allGenes = curtainData.allGenes ?: return validIds
        
        proteinIds.forEach { proteinId ->
            // Direct match in allGenes list
            if (allGenes.any { it.equals(proteinId, ignoreCase = true) }) {
                // Find the exact case-sensitive match
                val exactMatch = allGenes.find { it.equals(proteinId, ignoreCase = true) }
                exactMatch?.let { validIds.add(it) }
            }
        }
        
        return validIds.distinct()
    }
    
    /**
     * Syncs filter lists from remote repository
     */
    suspend fun syncFilterLists(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            dataFilterListRepository.syncDataFilterLists()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets filter list categories from local database only
     */
    suspend fun getFilterListCategories(): List<String> = withContext(Dispatchers.IO) {
        return@withContext dataFilterListRepository.getAllCategoriesLocal()
    }
    
    /**
     * Exports a search list as a filter list
     */
    suspend fun exportSearchListAsFilterList(
        searchList: SearchList,
        category: String = "Custom"
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val filterData = searchList.proteinIds.joinToString("\n")
            val request = DataFilterListRequest(
                name = searchList.name,
                category = category,
                data = filterData,
                isDefault = false
            )
            
            val result = dataFilterListRepository.createDataFilterList(request)
            result != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Restores search lists from stored CurtainDataService data
     * This loads the selectOperationNames and selectedMap data as SearchList objects
     * Based on the volcano plot usage pattern where selectOperationNames contains all available selection operations
     */
    fun restoreSearchListsFromCurtainData(curtainData: AppData) {
        // Store reference for auto-saving
        currentCurtainData = curtainData
        
        val currentSession = _searchSession.value
        val restoredLists = mutableListOf<SearchList>()
        
        // Clear existing search lists to start fresh from stored data
        val updatedSession = SearchSession()
        
        // Extract search operations from selectOperationNames and selectedMap
        // The selectOperationNames are the names used in volcano plot for grouping and coloring
        curtainData.selectOperationNames.forEach { operationName ->
            val proteinIds = mutableListOf<String>()
            
            // Find proteins that are selected for this operation
            curtainData.selectedMap.forEach { (proteinId, selections) ->
                selections[operationName]?.let { isSelected ->
                    if (isSelected) {
                        proteinIds.add(proteinId)
                    }
                }
            }
            
            // Create a search list even if empty (to preserve the operation name for future use)
            // This matches how the volcano plot expects selectOperationNames to be available
            val searchList = SearchList(
                id = UUID.randomUUID().toString(),
                name = operationName,
                color = updatedSession.getNextAvailableColor(),
                proteinIds = proteinIds.distinct(),
                searchTerms = emptyList(), // Original search terms not stored in legacy format
                searchType = SearchType.PRIMARY_ID, // Default type for restored lists
                description = if (proteinIds.isNotEmpty()) {
                    "Restored from stored selection data (${proteinIds.size} proteins)"
                } else {
                    "Empty selection operation from stored data"
                }
            )
            restoredLists.add(searchList)
        }
        
        // Add restored lists to the session
        restoredLists.forEach { searchList ->
            updatedSession.addSearchList(searchList)
        }
        _searchSession.value = updatedSession
    }
    
    /**
     * Saves current search lists back to CurtainDataService format
     * This updates the selectOperationNames and selectedMap data
     * Preserves all existing selections and maintains compatibility with volcano plot
     * Note: selectedMap only stores TRUE values - absence means false
     */
    fun saveSearchListsToCurtainData(curtainData: AppData) {
        val currentSession = _searchSession.value
        val newSelectOperationNames = mutableListOf<String>()
        val newSelectedMap = mutableMapOf<String, MutableMap<String, Boolean>>()
        
        // First, preserve ALL existing selections from the current selectedMap
        // This includes both search lists and other types of selections (e.g., significance groups from volcano plot)
        // Note: selectedMap only contains TRUE values, so we preserve all existing entries
        curtainData.selectedMap.forEach { (proteinId, selections) ->
            selections.forEach { (selectionName, isSelected) ->
                // Only process TRUE values (selectedMap should only contain true values anyway)
                if (isSelected) {
                    if (!newSelectedMap.containsKey(proteinId)) {
                        newSelectedMap[proteinId] = mutableMapOf()
                    }
                    newSelectedMap[proteinId]!![selectionName] = true
                    
                    // Add all existing operation names
                    if (selectionName !in newSelectOperationNames) {
                        newSelectOperationNames.add(selectionName)
                    }
                }
            }
        }
        
        // Now update with current SearchList objects, overriding existing search list selections
        currentSession.searchLists.forEach { searchList ->
            if (searchList.name !in newSelectOperationNames) {
                newSelectOperationNames.add(searchList.name)
            }
            
            // Remove any existing selections for this search list name from all proteins
            newSelectedMap.values.forEach { proteinSelections ->
                proteinSelections.remove(searchList.name)
            }
            
            // Add current selections (only TRUE values)
            searchList.proteinIds.forEach { proteinId ->
                if (!newSelectedMap.containsKey(proteinId)) {
                    newSelectedMap[proteinId] = mutableMapOf()
                }
                newSelectedMap[proteinId]!![searchList.name] = true
            }
        }
        
        // Clean up: Remove proteins that have no selections at all
        val cleanedSelectedMap = newSelectedMap.filterValues { selections ->
            selections.isNotEmpty()
        }.mapValues { (_, selections) ->
            // Filter out any false values (shouldn't exist but just in case)
            selections.filterValues { it }.toMap()
        }
        
        // Update the AppData directly (properties are mutable)
        curtainData.selectOperationNames = newSelectOperationNames
        curtainData.selectedMap = cleanedSelectedMap
    }
    
    /**
     * Clears all search lists and resets the session
     */
    fun clearAllSearchLists() {
        _searchSession.value = SearchSession()
        autoSaveSearchListsIfNeeded()
    }
    
    /**
     * Auto-saves search lists to CurtainDataService if a reference is available
     */
    private fun autoSaveSearchListsIfNeeded() {
        currentCurtainData?.let { curtainData ->
            saveSearchListsToCurtainData(curtainData)
        }
    }
    
    /**
     * Gets the stored selection operation names from CurtainDataService
     * This includes both user search lists and system-generated selections (e.g., volcano plot significance groups)
     */
    fun getStoredSelectionOperationNames(): List<String> {
        return currentCurtainData?.selectOperationNames ?: emptyList()
    }
    
    /**
     * Gets proteins selected for a specific operation name from stored data
     * This allows access to selections not managed by SearchService (e.g., volcano plot selections)
     */
    fun getProteinsForStoredOperation(operationName: String): List<String> {
        val curtainData = currentCurtainData ?: return emptyList()
        val selectedProteins = mutableListOf<String>()
        
        curtainData.selectedMap.forEach { (proteinId, selections) ->
            if (selections[operationName] == true) {
                selectedProteins.add(proteinId)
            }
        }
        
        return selectedProteins
    }
    
    /**
     * Checks if a specific operation name exists in stored data
     */
    fun hasStoredOperation(operationName: String): Boolean {
        return currentCurtainData?.selectOperationNames?.contains(operationName) ?: false
    }
    
    /**
     * Gets all stored selections in a structured format
     * Returns map of operation name to list of selected protein IDs
     */
    fun getAllStoredSelections(): Map<String, List<String>> {
        val curtainData = currentCurtainData ?: return emptyMap()
        val allSelections = mutableMapOf<String, MutableList<String>>()
        
        curtainData.selectedMap.forEach { (proteinId, selections) ->
            selections.forEach { (operationName, isSelected) ->
                if (isSelected) {
                    allSelections.getOrPut(operationName) { mutableListOf() }.add(proteinId)
                }
            }
        }
        
        return allSelections.mapValues { it.value.toList() }
    }
}
