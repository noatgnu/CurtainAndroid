package info.proteo.curtain.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.proteo.curtain.domain.model.AlignedPeptide
import info.proteo.curtain.domain.model.AlignedSequencePair
import info.proteo.curtain.domain.model.CustomPTMSite
import info.proteo.curtain.domain.model.ExperimentalPTMSite
import info.proteo.curtain.domain.model.PTMSiteComparison
import info.proteo.curtain.domain.model.PTMViewerState
import info.proteo.curtain.domain.model.ProteinDomain
import info.proteo.curtain.domain.model.UniProtFeature
import info.proteo.curtain.domain.service.ProteomicsDataService
import info.proteo.curtain.domain.service.SequenceAlignmentService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class PTMViewerViewModel @Inject constructor(
    private val proteomicsDataService: ProteomicsDataService,
    private val sequenceAlignmentService: SequenceAlignmentService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _linkId = MutableStateFlow(savedStateHandle.get<String>("linkId") ?: "")
    val linkId: StateFlow<String> = _linkId.asStateFlow()

    private val _accession = MutableStateFlow(savedStateHandle.get<String>("accession") ?: "")
    val accession: StateFlow<String> = _accession.asStateFlow()

    private val _ptmViewerState = MutableStateFlow<PTMViewerState?>(null)
    val ptmViewerState: StateFlow<PTMViewerState?> = _ptmViewerState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _ptmComparisons = MutableStateFlow<List<PTMSiteComparison>>(emptyList())
    val ptmComparisons: StateFlow<List<PTMSiteComparison>> = _ptmComparisons.asStateFlow()

    private val _selectedSite = MutableStateFlow<ExperimentalPTMSite?>(null)
    val selectedSite: StateFlow<ExperimentalPTMSite?> = _selectedSite.asStateFlow()

    private val _pCutoff = MutableStateFlow(0.05)
    val pCutoff: StateFlow<Double> = _pCutoff.asStateFlow()

    private val _fcCutoff = MutableStateFlow(0.6)
    val fcCutoff: StateFlow<Double> = _fcCutoff.asStateFlow()

    private val defaultModTypes = setOf("Phosphoserine", "Phosphotyrosine", "Phosphothreonine")
    private val _selectedModTypes = MutableStateFlow<Set<String>>(defaultModTypes)
    val selectedModTypes: StateFlow<Set<String>> = _selectedModTypes.asStateFlow()

    private val _selectedVariant = MutableStateFlow<String?>(null)
    val selectedVariant: StateFlow<String?> = _selectedVariant.asStateFlow()

    private val _customSequence = MutableStateFlow<String?>(null)
    val customSequence: StateFlow<String?> = _customSequence.asStateFlow()

    private val _selectedCustomDatabases = MutableStateFlow<Set<String>>(emptySet())
    val selectedCustomDatabases: StateFlow<Set<String>> = _selectedCustomDatabases.asStateFlow()

    private var currentCustomPTMData: Map<String, Any> = emptyMap()

    private var currentLinkId: String = ""
    private var currentAccession: String = ""
    private var currentPCutoff: Double = 0.05
    private var currentFcCutoff: Double = 0.6

    fun updateSelectedModTypes(modTypes: Set<String>) {
        _selectedModTypes.value = modTypes
    }

    private fun initializeSelectedModTypes(availableTypes: List<String>) {
        _selectedModTypes.value = defaultModTypes.filter { it in availableTypes }.toSet()
    }

    fun selectVariant(variant: String?) {
        _selectedVariant.value = variant
        _customSequence.value = null
        reloadWithVariant()
    }

    fun setCustomSequence(sequence: String?) {
        _customSequence.value = sequence
        _selectedVariant.value = null
        reloadWithVariant()
    }

    fun resetToDefault() {
        _selectedVariant.value = null
        _customSequence.value = null
        reloadWithVariant()
    }

    private fun reloadWithVariant() {
        if (currentLinkId.isNotEmpty() && currentAccession.isNotEmpty()) {
            loadData(
                linkId = currentLinkId,
                accession = currentAccession,
                pCutoff = currentPCutoff,
                fcCutoff = currentFcCutoff,
                customSequences = _customSequence.value?.let { mapOf(currentAccession to it) } ?: emptyMap(),
                variantCorrection = _selectedVariant.value?.let { mapOf(currentAccession to it) } ?: emptyMap()
            )
        }
    }

    fun updateSelectedCustomDatabases(databases: Set<String>) {
        _selectedCustomDatabases.value = databases
    }

    fun loadData(
        linkId: String,
        accession: String,
        pCutoff: Double = 0.05,
        fcCutoff: Double = 0.6,
        customSequences: Map<String, Any> = emptyMap(),
        variantCorrection: Map<String, Any> = emptyMap(),
        customPTMData: Map<String, Any> = emptyMap()
    ) {
        currentCustomPTMData = customPTMData
        _linkId.value = linkId
        _accession.value = accession
        _pCutoff.value = pCutoff
        _fcCutoff.value = fcCutoff

        currentLinkId = linkId
        currentAccession = accession
        currentPCutoff = pCutoff
        currentFcCutoff = fcCutoff

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val experimentalAccession = getFirstAccession(accession)
                val baseAccession = getBaseAccession(accession)

                var uniprotDataJson = proteomicsDataService.getUniProtDataJson(linkId, baseAccession)
                if (uniprotDataJson == null && experimentalAccession != baseAccession) {
                    uniprotDataJson = proteomicsDataService.getUniProtDataJson(linkId, experimentalAccession)
                }

                if (uniprotDataJson == null) {
                    _error.value = "UniProt data not found for accession: $accession"
                    _isLoading.value = false
                    return@launch
                }

                val canonicalSequence = sequenceAlignmentService.extractSequence(uniprotDataJson)
                if (canonicalSequence.isEmpty()) {
                    _error.value = "No sequence found for accession: $accession"
                    _isLoading.value = false
                    return@launch
                }

                val geneName = sequenceAlignmentService.extractGeneName(uniprotDataJson)
                val proteinName = sequenceAlignmentService.extractProteinName(uniprotDataJson)
                val organism = sequenceAlignmentService.extractOrganism(uniprotDataJson)
                val uniprotFeatures = sequenceAlignmentService.parseUniProtFeatures(uniprotDataJson)
                val domains = sequenceAlignmentService.extractDomains(uniprotDataJson)

                val parsedModifications = sequenceAlignmentService.parseModifications(uniprotDataJson)
                val availableModTypes = sequenceAlignmentService.getAvailableModTypes(parsedModifications)
                initializeSelectedModTypes(availableModTypes)

                val experimentalSites = loadExperimentalSites(linkId, experimentalAccession, pCutoff, fcCutoff)
                val alignedPeptides = createAlignedPeptides(linkId, experimentalAccession, canonicalSequence)

                val experimentalSequenceResult = resolveExperimentalSequence(
                    experimentalAccession = experimentalAccession,
                    baseAccession = baseAccession,
                    customSequences = customSequences,
                    variantCorrection = variantCorrection,
                    linkId = linkId
                )

                val alignedPair = if (experimentalSequenceResult != null) {
                    sequenceAlignmentService.alignSequences(
                        experimentalSequenceResult.first,
                        canonicalSequence
                    )
                } else null

                val customPTMSites = parseCustomPTMData(customPTMData, experimentalAccession, baseAccession)
                val availableCustomDatabases = customPTMSites.keys.toList()
                if (_selectedCustomDatabases.value.isEmpty() && availableCustomDatabases.isNotEmpty()) {
                    _selectedCustomDatabases.value = availableCustomDatabases.toSet()
                }

                val state = PTMViewerState(
                    accession = experimentalAccession,
                    geneName = geneName,
                    proteinName = proteinName,
                    organism = organism,
                    canonicalSequence = canonicalSequence,
                    sequenceLength = canonicalSequence.length,
                    experimentalSites = experimentalSites,
                    uniprotFeatures = uniprotFeatures,
                    alignedPeptides = alignedPeptides,
                    domains = domains,
                    alignedSequencePair = alignedPair,
                    experimentalSequenceSource = experimentalSequenceResult?.second,
                    parsedModifications = parsedModifications,
                    availableModTypes = availableModTypes,
                    customPTMSites = customPTMSites,
                    availableCustomDatabases = availableCustomDatabases,
                    selectedCustomDatabases = _selectedCustomDatabases.value
                )

                _ptmViewerState.value = state

                val comparisons = sequenceAlignmentService.comparePTMSites(
                    experimentalSites,
                    uniprotFeatures,
                    canonicalSequence
                )
                _ptmComparisons.value = comparisons

            } catch (e: Exception) {
                _error.value = "Error loading PTM data: ${e.message}"
                android.util.Log.e("PTMViewerViewModel", "Error loading data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadExperimentalSites(
        linkId: String,
        accession: String,
        pCutoff: Double,
        fcCutoff: Double
    ): List<ExperimentalPTMSite> {
        val sites = mutableListOf<ExperimentalPTMSite>()

        try {
            val db = proteomicsDataService.getDatabaseForLinkId(linkId)
            var allData = db.proteomicsDataDao().getProcessedDataByAccession(accession)
            if (allData.isEmpty()) {
                val baseAcc = getBaseAccession(accession)
                allData = db.proteomicsDataDao().getProcessedDataByAccession(baseAcc)
            }
            if (allData.isEmpty()) {
                allData = db.proteomicsDataDao().getProcessedDataByAccessionContaining(accession)
            }

            for (entity in allData) {
                val position = entity.position?.let { parsePosition(it) } ?: continue

                val isSignificant = entity.significant != null && entity.foldChange != null &&
                        entity.significant!! < pCutoff && abs(entity.foldChange!!) > fcCutoff

                sites.add(
                    ExperimentalPTMSite(
                        primaryId = entity.primaryId,
                        position = position,
                        residue = extractResidue(entity.position),
                        modification = null,
                        peptideSequence = entity.peptideSequence,
                        foldChange = entity.foldChange,
                        pValue = entity.significant,
                        isSignificant = isSignificant,
                        comparison = entity.comparison
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("PTMViewerViewModel", "Error loading experimental sites", e)
        }

        return sites.sortedBy { it.position }
    }

    private suspend fun createAlignedPeptides(
        linkId: String,
        accession: String,
        canonicalSequence: String
    ): List<AlignedPeptide> {
        val peptides = mutableListOf<AlignedPeptide>()

        try {
            val db = proteomicsDataService.getDatabaseForLinkId(linkId)
            var allData = db.proteomicsDataDao().getProcessedDataByAccession(accession)
            if (allData.isEmpty()) {
                val baseAcc = getBaseAccession(accession)
                allData = db.proteomicsDataDao().getProcessedDataByAccession(baseAcc)
            }
            if (allData.isEmpty()) {
                allData = db.proteomicsDataDao().getProcessedDataByAccessionContaining(accession)
            }

            for (entity in allData) {
                val peptideSequence = entity.peptideSequence ?: continue

                val isSignificant = entity.significant != null && entity.foldChange != null &&
                        entity.significant!! < _pCutoff.value && abs(entity.foldChange!!) > _fcCutoff.value

                val aligned = sequenceAlignmentService.createAlignedPeptide(
                    primaryId = entity.primaryId,
                    peptideSequence = peptideSequence,
                    positionString = entity.position,
                    canonicalSequence = canonicalSequence,
                    isSignificant = isSignificant
                )

                if (aligned != null) {
                    peptides.add(aligned)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PTMViewerViewModel", "Error creating aligned peptides", e)
        }

        return peptides.distinctBy { it.primaryId }
    }

    private fun parsePosition(positionString: String): Int? {
        val match = Regex("(\\d+)").find(positionString)
        return match?.value?.toIntOrNull()
    }

    private fun extractResidue(positionString: String?): Char {
        if (positionString == null) return '?'
        val match = Regex("([A-Z])\\d+").find(positionString)
        return match?.groupValues?.get(1)?.firstOrNull() ?: '?'
    }

    fun selectSite(site: ExperimentalPTMSite?) {
        _selectedSite.value = site
    }

    fun getSequenceSegment(startPosition: Int, length: Int): String {
        val state = _ptmViewerState.value ?: return ""
        val endPosition = minOf(startPosition + length - 1, state.canonicalSequence.length)
        val start = maxOf(startPosition - 1, 0)
        return state.canonicalSequence.substring(start, endPosition)
    }

    fun getSitesInRange(startPosition: Int, endPosition: Int): List<ExperimentalPTMSite> {
        val state = _ptmViewerState.value ?: return emptyList()
        return state.experimentalSites.filter { it.position in startPosition..endPosition }
    }

    fun getDomainsInRange(startPosition: Int, endPosition: Int): List<ProteinDomain> {
        val state = _ptmViewerState.value ?: return emptyList()
        return state.domains.filter {
            it.startPosition <= endPosition && it.endPosition >= startPosition
        }
    }

    fun getUniProtFeaturesInRange(startPosition: Int, endPosition: Int): List<UniProtFeature> {
        val state = _ptmViewerState.value ?: return emptyList()
        return state.uniprotFeatures.filter {
            it.startPosition <= endPosition && it.endPosition >= startPosition
        }
    }

    private fun getBaseAccession(accession: String): String {
        val firstAccession = accession.split(";").first().trim()
        return firstAccession.replace(Regex("-\\d+$"), "")
    }

    private fun getFirstAccession(accession: String): String {
        return accession.split(";").first().trim()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCustomPTMData(
        customPTMData: Map<String, Any>,
        experimentalAccession: String,
        baseAccession: String
    ): Map<String, List<CustomPTMSite>> {
        val result = mutableMapOf<String, MutableList<CustomPTMSite>>()

        for ((databaseName, dbData) in customPTMData) {
            try {
                val accessionMap = dbData as? Map<String, Any> ?: continue

                val relevantAccessions = listOf(experimentalAccession, baseAccession).distinct()

                for (accKey in relevantAccessions) {
                    val accData = accessionMap[accKey] as? Map<String, Any> ?: continue

                    for ((fullAccession, siteList) in accData) {
                        val sites = siteList as? List<Map<String, Any>> ?: continue

                        for (site in sites) {
                            val position = when (val pos = site["position"]) {
                                is Number -> pos.toInt() + 1
                                is String -> pos.toIntOrNull()?.plus(1) ?: continue
                                else -> continue
                            }
                            val residue = site["residue"]?.toString() ?: ""

                            if (!result.containsKey(databaseName)) {
                                result[databaseName] = mutableListOf()
                            }
                            result[databaseName]!!.add(
                                CustomPTMSite(
                                    databaseName = databaseName,
                                    position = position,
                                    residue = residue
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PTMViewerViewModel", "Error parsing custom PTM data for $databaseName", e)
            }
        }

        return result.mapValues { it.value.distinctBy { site -> site.position }.sortedBy { site -> site.position } }
    }

    private suspend fun resolveExperimentalSequence(
        experimentalAccession: String,
        baseAccession: String,
        customSequences: Map<String, Any>,
        variantCorrection: Map<String, Any>,
        linkId: String
    ): Pair<String, String>? {
        val customSeq = customSequences[experimentalAccession]
        if (customSeq is String && customSeq.isNotEmpty()) {
            return Pair(customSeq, "Custom Sequence")
        }

        val correctedId = variantCorrection[experimentalAccession]
        if (correctedId is String && correctedId.isNotEmpty()) {
            val correctedSeq = proteomicsDataService.getUniProtSequence(linkId, correctedId)
            if (!correctedSeq.isNullOrEmpty()) {
                return Pair(correctedSeq, "Variant: $correctedId")
            }
        }

        if (experimentalAccession != baseAccession) {
            val isoformSeq = proteomicsDataService.getUniProtSequence(linkId, experimentalAccession)
            if (!isoformSeq.isNullOrEmpty()) {
                return Pair(isoformSeq, experimentalAccession)
            }
        }

        val uniprotSeq = proteomicsDataService.getUniProtSequence(linkId, baseAccession)
        if (!uniprotSeq.isNullOrEmpty()) {
            return Pair(uniprotSeq, baseAccession)
        }

        return null
    }
}
