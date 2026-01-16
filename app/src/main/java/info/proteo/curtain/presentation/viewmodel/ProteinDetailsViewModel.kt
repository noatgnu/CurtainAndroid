package info.proteo.curtain.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.domain.model.CurtainData
import info.proteo.curtain.domain.model.SelectionGroup
import info.proteo.curtain.domain.repository.SelectionGroupRepository
import info.proteo.curtain.domain.service.CurtainDataService
import info.proteo.curtain.presentation.ui.curtain.ProteinInfo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class ProteinDetailsViewModel @Inject constructor(
    private val selectionGroupRepository: SelectionGroupRepository,
    private val curtainDataService: CurtainDataService,
    private val proteomicsDataService: info.proteo.curtain.domain.service.ProteomicsDataService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val curtainLinkId: String = checkNotNull(savedStateHandle["linkId"])

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _curtainData = MutableStateFlow<CurtainData?>(null)
    val curtainData: StateFlow<CurtainData?> = _curtainData.asStateFlow()

    val selectionGroups: StateFlow<List<SelectionGroup>> = selectionGroupRepository
        .getSelectionGroupsByCurtainId(curtainLinkId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val proteins: StateFlow<List<ProteinInfo>> = combine(
        _curtainData,
        selectionGroups,
        _searchQuery
    ) { data, groups, query ->
        if (data == null) return@combine emptyList()

        val allProteins = extractProteinsFromCurtainData(data, groups)

        if (query.isEmpty()) {
            allProteins
        } else {
            allProteins.filter { protein ->
                protein.primaryId.contains(query, ignoreCase = true) ||
                        protein.geneName?.contains(query, ignoreCase = true) == true
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCurtainData(data: CurtainData) {
        _curtainData.value = data
    }

    fun addProteinToGroup(proteinId: String, groupId: String) {
        viewModelScope.launch {
            selectionGroupRepository.addProteinsToGroup(groupId, listOf(proteinId))
        }
    }

    fun removeProteinFromGroup(proteinId: String, groupId: String) {
        viewModelScope.launch {
            selectionGroupRepository.removeProteinsFromGroup(groupId, listOf(proteinId))
        }
    }

    fun createSelectionGroup(name: String, color: String, proteins: List<String> = emptyList()) {
        viewModelScope.launch {
            selectionGroupRepository.createSelectionGroup(
                curtainLinkId = curtainLinkId,
                name = name,
                color = color,
                proteins = proteins
            )
        }
    }

    private suspend fun extractProteinsFromCurtainData(
        data: CurtainData,
        groups: List<SelectionGroup>
    ): List<ProteinInfo> {
        val proteins = mutableListOf<ProteinInfo>()

        val settings = data.settings
        val pCutoff = settings.pCutoff
        val log2FCCutoff = settings.log2FCCutoff
        val linkId = data.linkId

        val selectedProteinIds = data.selectedMap?.keys?.toList() ?: emptyList()
        if (selectedProteinIds.isEmpty()) {
            return emptyList()
        }

        val db = proteomicsDataService.getDatabaseForLinkId(linkId)

        val allData = db.proteomicsDataDao().getProcessedDataByPrimaryIds(selectedProteinIds)

        val dataByProtein = allData.groupBy { it.primaryId }

        dataByProtein.forEach { (proteinId, proteinDataList) ->
            val proteinData = proteinDataList.firstOrNull()
            if (proteinData != null) {
                var geneName: String? = null

                if (data.fetchUniprot) {
                    geneName = getGeneNameFromUniProt(proteinId, data)
                }

                if (geneName.isNullOrEmpty()) {
                    geneName = proteinData.geneNames?.takeIf { it.isNotEmpty() }
                }

                val fc = proteinData.foldChange
                val p = proteinData.significant

                val isSignificant = if (fc != null && p != null) {
                    p < pCutoff && abs(fc) > log2FCCutoff
                } else {
                    false
                }

                val proteinGroups = groups.filter { group ->
                    proteinId in group.proteins
                }

                proteins.add(
                    ProteinInfo(
                        primaryId = proteinId,
                        geneName = geneName,
                        log2FC = fc,
                        pValue = p,
                        isSignificant = isSignificant,
                        selectionGroups = proteinGroups
                    )
                )
            }
        }

        return proteins.sortedWith(
            compareByDescending<ProteinInfo> { it.isSignificant }
                .thenByDescending { it.log2FC?.let { fc -> abs(fc) } ?: 0.0 }
        )
    }

    private fun getUniprotFromPrimary(id: String, curtainData: CurtainData): Map<String, Any>? {
        val uniprotDB = curtainData.extraData?.uniprot?.db as? Map<String, Any>
        val dataMap = curtainData.extraData?.uniprot?.dataMap as? Map<String, Any>
        val accMap = curtainData.extraData?.uniprot?.accMap as? Map<String, Any>

        if (uniprotDB == null) return null

        if (uniprotDB.containsKey(id)) {
            return uniprotDB[id] as? Map<String, Any>
        }

        if (accMap != null && accMap.containsKey(id)) {
            val alternatives = accMap[id] as? List<*>
            if (alternatives != null) {
                for (alt in alternatives) {
                    if (dataMap != null && dataMap.containsKey(alt)) {
                        val canonicalEntry = dataMap[alt] as? String
                        if (canonicalEntry != null && uniprotDB.containsKey(canonicalEntry)) {
                            return uniprotDB[canonicalEntry] as? Map<String, Any>
                        }
                    }
                }
            }
        }

        return null
    }

    private fun getGeneNameFromUniProt(id: String, curtainData: CurtainData): String? {
        val uniprotRecord = getUniprotFromPrimary(id, curtainData)
        if (uniprotRecord != null) {
            val geneNames = uniprotRecord["Gene Names"] as? String
            if (!geneNames.isNullOrEmpty()) {
                val firstGeneName = geneNames.split(" ", ";", "\\")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .firstOrNull()
                if (!firstGeneName.isNullOrEmpty()) {
                    return firstGeneName
                }
            }
        }
        return null
    }
}
