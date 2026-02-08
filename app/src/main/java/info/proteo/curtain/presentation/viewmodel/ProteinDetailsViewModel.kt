package info.proteo.curtain.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.domain.model.CurtainData
import info.proteo.curtain.domain.model.SelectionGroup
import info.proteo.curtain.domain.repository.SelectionGroupRepository
import info.proteo.curtain.domain.service.CurtainDataService
import info.proteo.curtain.presentation.ui.curtain.AccessionGroup
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
    private val proteinMappingService: info.proteo.curtain.domain.service.ProteinMappingService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _curtainLinkId = MutableStateFlow(savedStateHandle.get<String>("linkId") ?: "")
    val curtainLinkId: StateFlow<String> = _curtainLinkId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _curtainData = MutableStateFlow<CurtainData?>(null)
    val curtainData: StateFlow<CurtainData?> = _curtainData.asStateFlow()

    private val _selectionGroups = MutableStateFlow<List<SelectionGroup>>(emptyList())
    val selectionGroups: StateFlow<List<SelectionGroup>> = _selectionGroups.asStateFlow()

    private val _sequenceCache = MutableStateFlow<Map<String, String?>>(emptyMap())
    val sequenceCache: StateFlow<Map<String, String?>> = _sequenceCache.asStateFlow()

    fun setLinkId(linkId: String) {
        if (_curtainLinkId.value != linkId) {
            _curtainLinkId.value = linkId
        }
    }

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
            val isPTM = data.differentialForm.isPTM
            allProteins.filter { protein ->
                protein.primaryId.contains(query, ignoreCase = true) ||
                        protein.geneName?.contains(query, ignoreCase = true) == true ||
                        (isPTM && protein.accession?.contains(query, ignoreCase = true) == true) ||
                        (isPTM && protein.position?.contains(query, ignoreCase = true) == true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val accessionGroups: StateFlow<List<AccessionGroup>> = combine(
        proteins,
        _curtainData
    ) { proteinList, data ->
        if (data == null || !data.differentialForm.isPTM) return@combine emptyList()

        proteinList
            .groupBy { it.accession ?: "Unknown" }
            .map { (accession, sites) ->
                val geneName = sites.firstOrNull()?.geneName
                AccessionGroup(
                    accession = accession,
                    geneName = geneName,
                    sites = sites.sortedWith(
                        compareByDescending<ProteinInfo> { it.isSignificant }
                            .thenBy { it.position ?: "" }
                    ),
                    significantCount = sites.count { it.isSignificant }
                )
            }
            .sortedWith(
                compareByDescending<AccessionGroup> { it.significantCount }
                    .thenByDescending { it.sites.size }
            )
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
        _selectionGroups.value = extractSelectionGroupsFromCurtainData(data)
    }

    fun fetchSequencesForAccessions(accessions: Set<String>) {
        val linkId = _curtainLinkId.value
        if (linkId.isEmpty()) return

        viewModelScope.launch {
            val newCache = mutableMapOf<String, String?>()
            for (accession in accessions) {
                if (!_sequenceCache.value.containsKey(accession)) {
                    val sequence = proteomicsDataService.getUniProtSequence(linkId, accession)
                    newCache[accession] = sequence
                }
            }
            if (newCache.isNotEmpty()) {
                _sequenceCache.value = _sequenceCache.value + newCache
            }
        }
    }

    fun fetchSequenceForVariant(variantId: String) {
        val linkId = _curtainLinkId.value
        if (linkId.isEmpty()) return

        viewModelScope.launch {
            val sequence = proteomicsDataService.getUniProtSequence(linkId, variantId)
            if (sequence != null) {
                _sequenceCache.value = _sequenceCache.value + (variantId to sequence)
            }
        }
    }

    private fun extractSelectionGroupsFromCurtainData(data: CurtainData): List<SelectionGroup> {
        val selectionsName = data.selectionsName ?: return emptyList()
        val colorMap = data.settings.colorMap
        val selectedMap = data.selectedMap ?: return emptyList()
        val defaultColors = data.settings.defaultColorList

        return selectionsName.mapIndexed { index, groupName ->
            val color = colorMap[groupName]
                ?: defaultColors.getOrElse(index % defaultColors.size) { "#808080" }

            val proteinsInGroup = selectedMap.filterValues { selections ->
                selections[groupName] == true
            }.keys.toList()

            SelectionGroup(
                id = groupName,
                curtainLinkId = data.linkId,
                name = groupName,
                color = color,
                proteins = proteinsInGroup,
                isActive = true,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )
        }
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
            val linkId = _curtainLinkId.value
            if (linkId.isNotEmpty()) {
                selectionGroupRepository.createSelectionGroup(
                    curtainLinkId = linkId,
                    name = name,
                    color = color,
                    proteins = proteins
                )
            }
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

        for ((proteinId, proteinDataList) in dataByProtein) {
            val proteinData = proteinDataList.firstOrNull() ?: continue
            var geneName: String? = proteinData.geneNames?.takeIf { it.isNotEmpty() }

            if (geneName.isNullOrEmpty()) {
                geneName = proteinMappingService.getGeneNameFromPrimaryId(linkId, proteinId)
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
                    selectionGroups = proteinGroups,
                    accession = proteinData.accession,
                    position = proteinData.position,
                    positionPeptide = proteinData.positionPeptide,
                    peptideSequence = proteinData.peptideSequence,
                    score = proteinData.score
                )
            )
        }

        return proteins.sortedWith(
            compareByDescending<ProteinInfo> { it.isSignificant }
                .thenByDescending { it.log2FC?.let { fc -> abs(fc) } ?: 0.0 }
        )
    }

}
