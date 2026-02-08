package info.proteo.curtain.domain.service

import android.util.Log
import com.google.gson.Gson
import info.proteo.curtain.data.local.entity.AllGenesEntity
import info.proteo.curtain.data.local.entity.GeneNameToAccEntity
import info.proteo.curtain.data.local.entity.GenesMapEntity
import info.proteo.curtain.data.local.entity.PrimaryIdsMapEntity
import info.proteo.curtain.data.local.entity.ProcessedProteomicsDataEntity
import info.proteo.curtain.data.local.entity.RawProteomicsDataEntity
import info.proteo.curtain.data.local.entity.UniProtEntryEntity
import info.proteo.curtain.domain.database.ProteomicsDataDatabaseManager
import info.proteo.curtain.domain.model.CurtainData
import info.proteo.curtain.domain.model.CurtainDifferentialForm
import info.proteo.curtain.domain.model.CurtainRawForm
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log2

@Singleton
class ProteomicsDataService @Inject constructor(
    private val databaseManager: ProteomicsDataDatabaseManager,
    private val gson: Gson
) {

    suspend fun loadCurtainDataFromDatabase(linkId: String): CurtainData? {
        Log.d("ProteomicsDataService", "loadCurtainDataFromDatabase called for linkId=$linkId")

        if (!databaseManager.checkDataExists(linkId)) {
            Log.d("ProteomicsDataService", "Data does not exist or schema mismatch for linkId=$linkId, clearing stale data")
            databaseManager.clearAllData(linkId)
            return null
        }

        val db = databaseManager.getDatabaseForLinkId(linkId)
        Log.d("ProteomicsDataService", "Got database instance for linkId=$linkId")
        val metadataEntity = db.proteomicsDataDao().getCurtainMetadata()
        Log.d("ProteomicsDataService", "Metadata entity: ${if (metadataEntity != null) "found" else "NOT FOUND"}")
        if (metadataEntity == null) return null

        val settings = gson.fromJson(metadataEntity.settingsJson, info.proteo.curtain.domain.model.CurtainSettings::class.java)

        val selectedMapType = object : com.google.gson.reflect.TypeToken<Map<String, Map<String, Boolean>>>() {}.type
        val selectedMap: Map<String, Map<String, Boolean>>? = metadataEntity.selectedMapJson?.let {
            gson.fromJson(it, selectedMapType)
        }

        val selectionsNameType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        val selectionsName: List<String>? = metadataEntity.selectionsNameJson?.let {
            gson.fromJson(it, selectionsNameType)
        }

        val selectionsType = object : com.google.gson.reflect.TypeToken<Map<String, List<Any>>>() {}.type
        val selections: Map<String, List<Any>>? = metadataEntity.selectionsJson?.let {
            gson.fromJson(it, selectionsType)
        }

        val selectionsMapType = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
        val selectionsMap: Map<String, Any>? = metadataEntity.selectionsMapJson?.let {
            gson.fromJson(it, selectionsMapType)
        }

        return CurtainData(
            raw = null,
            rawForm = gson.fromJson(metadataEntity.rawFormJson, CurtainRawForm::class.java),
            differentialForm = gson.fromJson(metadataEntity.differentialFormJson, CurtainDifferentialForm::class.java),
            processed = null,
            password = metadataEntity.password,
            selections = selections,
            selectionsMap = selectionsMap,
            selectedMap = selectedMap,
            selectionsName = selectionsName,
            _settings = settings,
            fetchUniprot = metadataEntity.fetchUniprot,
            annotatedData = metadataEntity.annotatedDataJson?.let {
                gson.fromJson(it, Any::class.java)
            },
            extraData = metadataEntity.extraDataJson?.let {
                gson.fromJson(it, info.proteo.curtain.domain.model.ExtraData::class.java)
            },
            permanent = metadataEntity.permanent,
            bypassUniProt = metadataEntity.bypassUniProt
        )
    }

    suspend fun buildProteomicsDataIfNeeded(
        linkId: String,
        rawTsv: String?,
        processedTsv: String?,
        rawForm: CurtainRawForm,
        differentialForm: CurtainDifferentialForm,
        curtainData: CurtainData,
        onProgress: (String) -> Unit = {}
    ) {
        if (databaseManager.checkDataExists(linkId)) {
            Log.d("ProteomicsDataService", "Proteomics data already exists for $linkId")
            return
        }

        Log.d("ProteomicsDataService", "Building proteomics data for $linkId")
        databaseManager.clearAllData(linkId)

        onProgress("Parsing processed data...")
        var processedData = parseProcessedData(processedTsv, differentialForm)

        if (differentialForm.isPTM) {
            processedData = enrichPTMGeneNames(processedData, curtainData)
        }

        onProgress("Parsing raw data...")
        val rawData = parseRawData(rawTsv, rawForm)

        onProgress("Building settings...")
        val updatedCurtainData = buildSettingsFromSamples(curtainData, rawForm.samples)

        val db = databaseManager.getDatabaseForLinkId(linkId)

        if (processedData.isNotEmpty()) {
            onProgress("Storing ${processedData.size} proteins...")
            Log.d("ProteomicsDataService", "Inserting ${processedData.size} processed data entries")
            db.proteomicsDataDao().insertProcessedData(processedData)
        }

        if (rawData.isNotEmpty()) {
            onProgress("Storing ${rawData.size} raw data entries...")
            Log.d("ProteomicsDataService", "Inserting ${rawData.size} raw data entries")
            db.proteomicsDataDao().insertRawData(rawData)
        }

        onProgress("Storing gene mappings...")
        parseAndStoreExtraDataMaps(updatedCurtainData, db)

        onProgress("Storing metadata...")
        storeCurtainMetadata(updatedCurtainData, db)

        databaseManager.storeSchemaVersion(linkId)
        Log.d("ProteomicsDataService", "Proteomics data build complete for $linkId")
    }

    private fun buildSettingsFromSamples(curtainData: CurtainData, samples: List<String>): CurtainData {
        val settings = curtainData.settings

        val builtSampleMap = mutableMapOf<String, Map<String, String>>()
        val conditions = mutableListOf<String>()
        val colorMap = settings.colorMap.toMutableMap()
        val sampleOrder = settings.sampleOrder.toMutableMap()
        val sampleVisible = settings.sampleVisible.toMutableMap()

        var colorPosition = 0

        for (sample in samples) {
            val parts = sample.split(".")
            val replicate = parts.lastOrNull() ?: ""
            val condition = if (parts.size > 1) {
                parts.dropLast(1).joinToString(".")
            } else {
                sample
            }

            val existingCondition = if (settings.sampleMap.containsKey(sample)) {
                settings.sampleMap[sample]?.get("condition") ?: condition
            } else {
                condition
            }

            if (!conditions.contains(existingCondition)) {
                conditions.add(existingCondition)

                if (!colorMap.containsKey(existingCondition)) {
                    if (colorPosition >= settings.defaultColorList.size) {
                        colorPosition = 0
                    }
                    colorMap[existingCondition] = settings.defaultColorList[colorPosition]
                    colorPosition++
                }
            }

            if (!sampleOrder.containsKey(existingCondition)) {
                sampleOrder[existingCondition] = mutableListOf()
            }
            if (!sampleOrder[existingCondition]!!.contains(sample)) {
                (sampleOrder[existingCondition] as MutableList).add(sample)
            }

            if (!sampleVisible.containsKey(sample)) {
                sampleVisible[sample] = true
            }

            builtSampleMap[sample] = mapOf(
                "replicate" to replicate,
                "condition" to existingCondition,
                "name" to sample
            )
        }

        val finalSampleMap = if (settings.sampleMap.isEmpty()) {
            builtSampleMap
        } else {
            val mergedMap = settings.sampleMap.toMutableMap()
            for ((key, value) in builtSampleMap) {
                if (!mergedMap.containsKey(key)) {
                    mergedMap[key] = value
                }
            }
            mergedMap.filterKeys { it in samples }
        }

        val cleanedSampleVisible = sampleVisible.filterKeys { it in samples }

        val finalConditionOrder = if (settings.conditionOrder.isEmpty()) {
            conditions
        } else {
            val existingConditions = settings.conditionOrder.filter { it in conditions }.toMutableList()
            val newConditions = conditions.filter { it !in existingConditions }
            existingConditions + newConditions
        }

        val cleanedSampleOrder = sampleOrder.filterKeys { it in conditions }

        val updatedSettings = settings.copy(
            sampleMap = finalSampleMap,
            colorMap = colorMap,
            sampleOrder = cleanedSampleOrder,
            sampleVisible = cleanedSampleVisible,
            conditionOrder = finalConditionOrder
        )

        return curtainData.copy(_settings = updatedSettings)
    }

    private suspend fun storeCurtainMetadata(curtainData: CurtainData, db: info.proteo.curtain.data.local.ProteomicsDataDatabase) {
        val metadata = info.proteo.curtain.data.local.entity.CurtainMetadataEntity(
            id = 1,
            settingsJson = gson.toJson(curtainData.settings),
            rawFormJson = gson.toJson(curtainData.rawForm),
            differentialFormJson = gson.toJson(curtainData.differentialForm),
            selectionsJson = curtainData.selections?.let { gson.toJson(it) },
            selectionsMapJson = curtainData.selectionsMap?.let { gson.toJson(it) },
            selectedMapJson = curtainData.selectedMap?.let { gson.toJson(it) },
            selectionsNameJson = curtainData.selectionsName?.let { gson.toJson(it) },
            extraDataJson = null,
            annotatedDataJson = null,
            password = curtainData.password,
            fetchUniprot = curtainData.fetchUniprot,
            permanent = curtainData.permanent,
            bypassUniProt = curtainData.bypassUniProt
        )
        db.proteomicsDataDao().insertCurtainMetadata(metadata)
    }

    private suspend fun parseAndStoreExtraDataMaps(curtainData: CurtainData, db: info.proteo.curtain.data.local.ProteomicsDataDatabase) {
        val extraData = curtainData.extraData ?: return

        if (extraData.data != null) {
            val dataMapContainer = extraData.data

            if (dataMapContainer.genesMap != null) {
                val genesMapEntities = (dataMapContainer.genesMap as? Map<String, Any>)?.map { (key, value) ->
                    GenesMapEntity(key, gson.toJson(value))
                } ?: emptyList()

                if (genesMapEntities.isNotEmpty()) {
                    Log.d("ProteomicsDataService", "Inserting ${genesMapEntities.size} genesMap entries")
                    db.proteomicsDataDao().insertGenesMap(genesMapEntities)
                }
            }

            if (dataMapContainer.primaryIDsMap != null) {
                val primaryIdsMapEntities = (dataMapContainer.primaryIDsMap as? Map<String, Any>)?.map { (key, value) ->
                    PrimaryIdsMapEntity(key, gson.toJson(value))
                } ?: emptyList()

                if (primaryIdsMapEntities.isNotEmpty()) {
                    Log.d("ProteomicsDataService", "Inserting ${primaryIdsMapEntities.size} primaryIDsMap entries")
                    db.proteomicsDataDao().insertPrimaryIdsMap(primaryIdsMapEntities)
                }
            }

            if (!dataMapContainer.allGenes.isNullOrEmpty()) {
                val allGenesEntities = dataMapContainer.allGenes.map { geneName ->
                    AllGenesEntity(geneName = geneName)
                }
                Log.d("ProteomicsDataService", "Inserting ${allGenesEntities.size} allGenes entries")
                db.proteomicsDataDao().insertAllGenes(allGenesEntities)
            }
        }

        if (extraData.uniprot != null) {
            val uniprotData = extraData.uniprot

            if (uniprotData.geneNameToAcc != null) {
                val geneNameToAccEntities = (uniprotData.geneNameToAcc as? Map<String, Any>)?.map { (geneName, value) ->
                    GeneNameToAccEntity(geneName, gson.toJson(value))
                } ?: emptyList()

                if (geneNameToAccEntities.isNotEmpty()) {
                    Log.d("ProteomicsDataService", "Inserting ${geneNameToAccEntities.size} geneNameToAcc entries")
                    db.proteomicsDataDao().insertGeneNameToAcc(geneNameToAccEntities)
                }
            }

            if (uniprotData.db != null) {
                val uniprotDbMap = uniprotData.db as? Map<String, Any>
                if (uniprotDbMap != null) {
                    val uniprotEntries = uniprotDbMap.map { (accession, record) ->
                        UniProtEntryEntity(
                            accession = accession,
                            dataJson = gson.toJson(record)
                        )
                    }
                    if (uniprotEntries.isNotEmpty()) {
                        Log.d("ProteomicsDataService", "Inserting ${uniprotEntries.size} UniProt entries")
                        db.proteomicsDataDao().insertUniProtEntries(uniprotEntries)
                    }
                }
            }
        }
    }

    private fun parseProcessedData(
        processedTsv: String?,
        form: CurtainDifferentialForm
    ): List<ProcessedProteomicsDataEntity> {
        if (processedTsv.isNullOrEmpty()) return emptyList()

        val lines = processedTsv.split("\n").filter { it.trim().isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val headers = lines[0].split("\t")
        val primaryIdIndex = headers.indexOf(form.primaryIDs)
        val geneNamesIndex = headers.indexOf(form.geneNames)
        val foldChangeIndex = headers.indexOf(form.foldChange)
        val significantIndex = headers.indexOf(form.significant)
        val comparisonIndex = headers.indexOf(form.comparison)

        if (primaryIdIndex == -1) {
            Log.w("ProteomicsDataService", "Primary ID column '${form.primaryIDs}' not found")
            return emptyList()
        }

        val isPTM = form.isPTM
        val accessionIndex = if (isPTM) headers.indexOf(form.accession) else -1
        val positionIndex = if (isPTM) headers.indexOf(form.position) else -1
        val positionPeptideIndex = if (isPTM) headers.indexOf(form.positionPeptide) else -1
        val peptideSequenceIndex = if (isPTM) headers.indexOf(form.peptideSequence) else -1
        val scoreIndex = if (isPTM) headers.indexOf(form.score) else -1

        if (isPTM) {
            Log.d("ProteomicsDataService", "PTM mode: accession=${form.accession}($accessionIndex), position=${form.position}($positionIndex), score=${form.score}($scoreIndex)")
        }

        val result = mutableListOf<ProcessedProteomicsDataEntity>()

        for (i in 1 until lines.size) {
            val values = lines[i].split("\t")
            if (values.size <= primaryIdIndex) continue

            val primaryId = values[primaryIdIndex]
            val geneNames = if (geneNamesIndex >= 0 && geneNamesIndex < values.size) {
                values[geneNamesIndex].takeIf { it.isNotEmpty() }
            } else null

            var foldChange: Double? = null
            if (foldChangeIndex >= 0 && foldChangeIndex < values.size) {
                foldChange = values[foldChangeIndex].toDoubleOrNull()
                if (foldChange != null) {
                    if (form.transformFC) {
                        foldChange = if (foldChange > 0) log2(foldChange) else null
                    }
                    if (form.reverseFoldChange && foldChange != null) {
                        foldChange = -foldChange
                    }
                }
            }

            var significant: Double? = null
            if (significantIndex >= 0 && significantIndex < values.size) {
                significant = values[significantIndex].toDoubleOrNull()
                if (significant != null && form.transformSignificant && significant > 0) {
                    significant = -kotlin.math.log10(significant)
                }
            }

            val comparisonValue = if (comparisonIndex >= 0 && comparisonIndex < values.size) {
                val value = values[comparisonIndex]
                if (value.isEmpty()) "1" else value
            } else "1"

            val accession = if (accessionIndex >= 0 && accessionIndex < values.size) {
                values[accessionIndex].takeIf { it.isNotEmpty() }
            } else null

            val position = if (positionIndex >= 0 && positionIndex < values.size) {
                values[positionIndex].takeIf { it.isNotEmpty() }
            } else null

            val positionPeptide = if (positionPeptideIndex >= 0 && positionPeptideIndex < values.size) {
                values[positionPeptideIndex].takeIf { it.isNotEmpty() }
            } else null

            val peptideSequence = if (peptideSequenceIndex >= 0 && peptideSequenceIndex < values.size) {
                values[peptideSequenceIndex].takeIf { it.isNotEmpty() }
            } else null

            val scoreValue = if (scoreIndex >= 0 && scoreIndex < values.size) {
                values[scoreIndex].toDoubleOrNull()
            } else null

            result.add(
                ProcessedProteomicsDataEntity(
                    primaryId = primaryId,
                    geneNames = geneNames,
                    foldChange = foldChange,
                    significant = significant,
                    comparison = comparisonValue,
                    accession = accession,
                    position = position,
                    positionPeptide = positionPeptide,
                    peptideSequence = peptideSequence,
                    score = scoreValue
                )
            )
        }

        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun enrichPTMGeneNames(
        processedData: List<ProcessedProteomicsDataEntity>,
        curtainData: CurtainData
    ): List<ProcessedProteomicsDataEntity> {
        val uniprotDb = curtainData.extraData?.uniprot?.db as? Map<String, Any> ?: return processedData
        val accMap = curtainData.extraData?.uniprot?.accMap as? Map<String, Any>

        val accessionToGeneName = mutableMapOf<String, String>()
        for ((accession, record) in uniprotDb) {
            val recordMap = record as? Map<String, Any> ?: continue
            val geneNames = recordMap["Gene Names"] as? String
            if (!geneNames.isNullOrEmpty()) {
                val firstGene = geneNames.split(" ", ";", "\\")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .firstOrNull()
                if (!firstGene.isNullOrEmpty()) {
                    accessionToGeneName[accession] = firstGene
                }
            }
        }

        Log.d("ProteomicsDataService", "Built accession→geneName map with ${accessionToGeneName.size} entries")

        return processedData.map { entity ->
            if (!entity.geneNames.isNullOrEmpty()) return@map entity

            val accession = entity.accession ?: return@map entity

            var geneName = accessionToGeneName[accession]

            if (geneName == null && accMap != null) {
                val canonical = accMap[accession] as? String
                if (canonical != null) {
                    geneName = accessionToGeneName[canonical]
                }
            }

            if (geneName == null) {
                val regex = Regex("[OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9]([A-Z][A-Z0-9]{2}[0-9]){1,2}")
                val match = regex.find(accession)
                if (match != null) {
                    geneName = accessionToGeneName[match.value]
                }
            }

            if (geneName != null) {
                entity.copy(geneNames = geneName)
            } else {
                entity
            }
        }
    }

    private fun parseRawData(
        rawTsv: String?,
        form: CurtainRawForm
    ): List<RawProteomicsDataEntity> {
        if (rawTsv.isNullOrEmpty() || form.samples.isEmpty()) return emptyList()

        val lines = rawTsv.split("\n").filter { it.trim().isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val headers = lines[0].split("\t")
        val primaryIdIndex = headers.indexOf(form.primaryIDs)

        if (primaryIdIndex == -1) {
            Log.w("ProteomicsDataService", "Primary ID column '${form.primaryIDs}' not found in raw data")
            return emptyList()
        }

        val sampleIndices = form.samples.mapNotNull { sampleName ->
            val index = headers.indexOf(sampleName)
            if (index >= 0) sampleName to index else null
        }

        val result = mutableListOf<RawProteomicsDataEntity>()

        for (i in 1 until lines.size) {
            val values = lines[i].split("\t")
            if (values.size <= primaryIdIndex) continue

            val primaryId = values[primaryIdIndex]

            for ((sampleName, sampleIndex) in sampleIndices) {
                if (sampleIndex < values.size) {
                    var sampleValue = values[sampleIndex].toDoubleOrNull()

                    if (sampleValue != null && form.log2) {
                        sampleValue = if (sampleValue > 0) log2(sampleValue) else null
                    }

                    result.add(
                        RawProteomicsDataEntity(
                            primaryId = primaryId,
                            sampleName = sampleName,
                            sampleValue = sampleValue
                        )
                    )
                }
            }
        }

        return result
    }

    suspend fun getProcessedDataForProtein(linkId: String, primaryId: String): List<ProcessedProteomicsDataEntity> {
        val db = databaseManager.getDatabaseForLinkId(linkId)
        return db.proteomicsDataDao().getProcessedDataByPrimaryId(primaryId)
    }

    suspend fun getRawDataForProtein(linkId: String, primaryId: String): List<RawProteomicsDataEntity> {
        val db = databaseManager.getDatabaseForLinkId(linkId)
        return db.proteomicsDataDao().getRawDataByPrimaryId(primaryId)
    }

    suspend fun getDatabaseForLinkId(linkId: String): info.proteo.curtain.data.local.ProteomicsDataDatabase {
        return databaseManager.getDatabaseForLinkId(linkId)
    }

    suspend fun clearDatabaseForLinkId(linkId: String) {
        databaseManager.clearAllData(linkId)
        Log.d("ProteomicsDataService", "Cleared all proteomics data for $linkId")
    }

    suspend fun forceRebuildProteomicsData(
        linkId: String,
        rawTsv: String?,
        processedTsv: String?,
        rawForm: CurtainRawForm,
        differentialForm: CurtainDifferentialForm,
        curtainData: CurtainData,
        onProgress: (String) -> Unit = {}
    ): CurtainData {
        Log.d("ProteomicsDataService", "Force rebuilding proteomics data for $linkId")

        onProgress("Clearing existing data...")
        databaseManager.clearAllData(linkId)

        onProgress("Parsing processed data...")
        var processedData = parseProcessedData(processedTsv, differentialForm)

        if (differentialForm.isPTM) {
            processedData = enrichPTMGeneNames(processedData, curtainData)
        }

        onProgress("Parsing raw data...")
        val rawData = parseRawData(rawTsv, rawForm)

        onProgress("Building settings...")
        val updatedCurtainData = buildSettingsFromSamples(curtainData, rawForm.samples)

        val db = databaseManager.getDatabaseForLinkId(linkId)

        if (processedData.isNotEmpty()) {
            onProgress("Storing ${processedData.size} proteins...")
            Log.d("ProteomicsDataService", "Inserting ${processedData.size} processed data entries")
            db.proteomicsDataDao().insertProcessedData(processedData)
        }

        if (rawData.isNotEmpty()) {
            onProgress("Storing ${rawData.size} raw data entries...")
            Log.d("ProteomicsDataService", "Inserting ${rawData.size} raw data entries")
            db.proteomicsDataDao().insertRawData(rawData)
        }

        onProgress("Storing gene mappings...")
        parseAndStoreExtraDataMaps(updatedCurtainData, db)

        onProgress("Storing metadata...")
        storeCurtainMetadata(updatedCurtainData, db)

        databaseManager.storeSchemaVersion(linkId)
        Log.d("ProteomicsDataService", "Force rebuild complete for $linkId")

        return updatedCurtainData
    }

    suspend fun getUniProtSequence(linkId: String, accession: String): String? {
        val db = databaseManager.getDatabaseForLinkId(linkId)
        val dataJson = db.proteomicsDataDao().getUniProtDataJson(accession) ?: return null
        return try {
            val record = gson.fromJson(dataJson, Map::class.java) as? Map<String, Any>
            record?.get("Sequence") as? String
        } catch (e: Exception) {
            Log.w("ProteomicsDataService", "Failed to parse UniProt data for $accession: ${e.message}")
            null
        }
    }

    suspend fun getUniProtDataJson(linkId: String, accession: String): String? {
        val db = databaseManager.getDatabaseForLinkId(linkId)
        return db.proteomicsDataDao().getUniProtDataJson(accession)
    }

    suspend fun getUniProtData(linkId: String, accession: String): Map<String, Any>? {
        val db = databaseManager.getDatabaseForLinkId(linkId)
        val dataJson = db.proteomicsDataDao().getUniProtDataJson(accession) ?: return null
        return try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(dataJson, Map::class.java) as? Map<String, Any>
        } catch (e: Exception) {
            Log.w("ProteomicsDataService", "Failed to parse UniProt data for $accession: ${e.message}")
            null
        }
    }
}
