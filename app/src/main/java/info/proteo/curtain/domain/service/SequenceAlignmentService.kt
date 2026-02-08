package info.proteo.curtain.domain.service

import info.proteo.curtain.domain.model.AlignedPeptide
import info.proteo.curtain.domain.model.AlignedSequencePair
import info.proteo.curtain.domain.model.ExperimentalPTMSite
import info.proteo.curtain.domain.model.FeatureType
import info.proteo.curtain.domain.model.ParsedModification
import info.proteo.curtain.domain.model.PTMPosition
import info.proteo.curtain.domain.model.PTMSiteComparison
import info.proteo.curtain.domain.model.ProteinDomain
import info.proteo.curtain.domain.model.UniProtFeature
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class SequenceAlignmentService @Inject constructor() {

    companion object {
        private const val MATCH_SCORE = 2
        private const val MISMATCH_SCORE = -1
        private const val GAP_PENALTY = -2
    }

    fun alignSequences(
        experimentalSequence: String,
        canonicalSequence: String
    ): AlignedSequencePair {
        val seq1 = experimentalSequence.uppercase().filter { it.isLetter() }
        val seq2 = canonicalSequence.uppercase().filter { it.isLetter() }

        if (seq1.isEmpty() || seq2.isEmpty()) {
            return AlignedSequencePair(
                experimentalSequence = seq1,
                canonicalSequence = seq2,
                experimentalAligned = seq1,
                canonicalAligned = seq2,
                experimentalPositionMap = emptyMap(),
                canonicalPositionMap = emptyMap()
            )
        }

        val m = seq1.length
        val n = seq2.length

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i * GAP_PENALTY
        for (j in 0..n) dp[0][j] = j * GAP_PENALTY

        for (i in 1..m) {
            for (j in 1..n) {
                val matchScore = if (seq1[i - 1] == seq2[j - 1]) MATCH_SCORE else MISMATCH_SCORE
                dp[i][j] = max(
                    max(
                        dp[i - 1][j - 1] + matchScore,
                        dp[i - 1][j] + GAP_PENALTY
                    ),
                    dp[i][j - 1] + GAP_PENALTY
                )
            }
        }

        val aligned1 = StringBuilder()
        val aligned2 = StringBuilder()
        val posMap1 = mutableMapOf<Int, Int>()
        val posMap2 = mutableMapOf<Int, Int>()

        var i = m
        var j = n
        var alignedPos = 0

        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + (if (seq1[i - 1] == seq2[j - 1]) MATCH_SCORE else MISMATCH_SCORE) -> {
                    aligned1.insert(0, seq1[i - 1])
                    aligned2.insert(0, seq2[j - 1])
                    i--
                    j--
                }
                i > 0 && dp[i][j] == dp[i - 1][j] + GAP_PENALTY -> {
                    aligned1.insert(0, seq1[i - 1])
                    aligned2.insert(0, '-')
                    i--
                }
                else -> {
                    aligned1.insert(0, '-')
                    aligned2.insert(0, seq2[j - 1])
                    j--
                }
            }
        }

        var expPos = 0
        var canPos = 0
        for (idx in aligned1.indices) {
            if (aligned1[idx] != '-') {
                expPos++
                posMap1[expPos] = idx
            }
            if (aligned2[idx] != '-') {
                canPos++
                posMap2[canPos] = idx
            }
        }

        return AlignedSequencePair(
            experimentalSequence = seq1,
            canonicalSequence = seq2,
            experimentalAligned = aligned1.toString(),
            canonicalAligned = aligned2.toString(),
            experimentalPositionMap = posMap1,
            canonicalPositionMap = posMap2
        )
    }

    fun alignMultipleSequences(sequences: Map<String, String>): Map<String, String> {
        if (sequences.size < 2) return sequences

        val entries = sequences.entries.toList()
        var alignedSequences = mutableMapOf<String, String>()

        val first = entries[0]
        alignedSequences[first.key] = first.value.uppercase().filter { it.isLetter() }

        for (i in 1 until entries.size) {
            val current = entries[i]
            val currentSeq = current.value.uppercase().filter { it.isLetter() }

            val referenceSeq = alignedSequences.values.first().replace("-", "")
            val aligned = alignSequences(currentSeq, referenceSeq)

            val newAligned = mutableMapOf<String, String>()
            for ((key, seq) in alignedSequences) {
                newAligned[key] = insertGapsToMatch(seq, aligned.canonicalAligned)
            }
            newAligned[current.key] = aligned.experimentalAligned

            alignedSequences = newAligned
        }

        return alignedSequences
    }

    private fun insertGapsToMatch(sequence: String, template: String): String {
        val result = StringBuilder()
        var seqIdx = 0

        for (char in template) {
            if (char == '-') {
                result.append('-')
            } else if (seqIdx < sequence.length) {
                result.append(sequence[seqIdx])
                seqIdx++
            }
        }

        while (seqIdx < sequence.length) {
            result.append(sequence[seqIdx])
            seqIdx++
        }

        return result.toString()
    }

    fun alignPeptideToSequence(
        peptideSequence: String,
        canonicalSequence: String
    ): Pair<Int, Int>? {
        val cleanPeptide = cleanPeptideSequence(peptideSequence)
        if (cleanPeptide.isEmpty()) return null

        val index = canonicalSequence.indexOf(cleanPeptide)
        return if (index >= 0) {
            Pair(index + 1, index + cleanPeptide.length)
        } else {
            findBestAlignment(cleanPeptide, canonicalSequence)
        }
    }

    private fun cleanPeptideSequence(peptide: String): String {
        return peptide
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .replace("_", "")
            .replace(".", "")
            .replace("-", "")
            .uppercase()
            .filter { it.isLetter() }
    }

    private fun findBestAlignment(peptide: String, sequence: String): Pair<Int, Int>? {
        if (peptide.length < 3) return null

        var bestMatch: Pair<Int, Int>? = null
        var bestScore = 0

        for (i in 0..sequence.length - peptide.length) {
            var score = 0
            for (j in peptide.indices) {
                if (sequence[i + j] == peptide[j]) {
                    score++
                }
            }
            if (score > bestScore && score >= peptide.length * 0.8) {
                bestScore = score
                bestMatch = Pair(i + 1, i + peptide.length)
            }
        }

        return bestMatch
    }

    fun extractPTMPositionFromPeptide(
        peptideSequence: String,
        positionString: String?,
        alignmentStart: Int
    ): List<PTMPosition> {
        val positions = mutableListOf<PTMPosition>()

        if (positionString != null) {
            val posMatch = Regex("([A-Z])(\\d+)").find(positionString)
            if (posMatch != null) {
                val residue = posMatch.groupValues[1][0]
                val proteinPosition = posMatch.groupValues[2].toIntOrNull() ?: return positions
                positions.add(
                    PTMPosition(
                        positionInPeptide = proteinPosition - alignmentStart + 1,
                        positionInProtein = proteinPosition,
                        residue = residue,
                        modification = extractModificationType(positionString)
                    )
                )
            }
        }

        val modPattern = Regex("\\[([^\\]]+)\\]|\\(([^)]+)\\)")
        var cleanIndex = 0
        var i = 0
        while (i < peptideSequence.length) {
            val match = modPattern.find(peptideSequence, i)
            if (match != null && match.range.first == i) {
                if (cleanIndex > 0) {
                    val residue = peptideSequence.getOrNull(i - 1)
                    if (residue != null && residue.isLetter()) {
                        positions.add(
                            PTMPosition(
                                positionInPeptide = cleanIndex,
                                positionInProtein = alignmentStart + cleanIndex - 1,
                                residue = residue.uppercaseChar(),
                                modification = match.groupValues[1].ifEmpty { match.groupValues[2] }
                            )
                        )
                    }
                }
                i = match.range.last + 1
            } else {
                if (peptideSequence[i].isLetter()) {
                    cleanIndex++
                }
                i++
            }
        }

        return positions.distinctBy { it.positionInProtein }
    }

    private fun extractModificationType(positionString: String): String? {
        return when {
            positionString.contains("S", ignoreCase = true) -> "Phosphoserine"
            positionString.contains("T", ignoreCase = true) -> "Phosphothreonine"
            positionString.contains("Y", ignoreCase = true) -> "Phosphotyrosine"
            else -> null
        }
    }

    fun parseUniProtFeatures(dataJson: String): List<UniProtFeature> {
        val features = mutableListOf<UniProtFeature>()

        try {
            val json = JSONObject(dataJson)

            parseModifiedResidues(json, features)
            parseDomains(json, features)
            parseOtherFeatures(json, features)

        } catch (e: Exception) {
            android.util.Log.e("SequenceAlignmentService", "Error parsing UniProt features", e)
        }

        return features
    }

    private fun parseModifiedResidues(json: JSONObject, features: MutableList<UniProtFeature>) {
        val modResString = json.optString("Modified residue", "")
        if (modResString.isNotEmpty()) {
            val parts = modResString.split("; ")
            var currentPosition = -1

            for (part in parts) {
                when {
                    part.startsWith("MOD_RES") -> {
                        currentPosition = part.split(" ").getOrNull(1)?.toIntOrNull() ?: -1
                    }
                    part.contains("note=") && currentPosition > 0 -> {
                        val noteMatch = Regex("\"(.+?)\"").find(part)
                        if (noteMatch != null) {
                            features.add(
                                UniProtFeature(
                                    type = FeatureType.MODIFIED_RESIDUE,
                                    startPosition = currentPosition,
                                    endPosition = currentPosition,
                                    description = noteMatch.groupValues[1]
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun parseDomains(json: JSONObject, features: MutableList<UniProtFeature>) {
        val domainString = json.optString("Domain [FT]", "")
        if (domainString.isNotEmpty()) {
            val parts = domainString.split(";")
            var start = -1
            var end = -1

            for (part in parts) {
                val trimmed = part.trim()
                when {
                    trimmed.contains("DOMAIN") -> {
                        val positions = Regex("(\\d+)").findAll(trimmed).toList()
                        if (positions.size >= 2) {
                            start = positions[0].value.toInt()
                            end = positions[1].value.toInt()
                        }
                    }
                    trimmed.contains("/note=") && start > 0 -> {
                        val noteMatch = Regex("\"(.+?)\"").find(trimmed)
                        if (noteMatch != null) {
                            features.add(
                                UniProtFeature(
                                    type = FeatureType.DOMAIN,
                                    startPosition = start,
                                    endPosition = end,
                                    description = noteMatch.groupValues[1]
                                )
                            )
                            start = -1
                            end = -1
                        }
                    }
                }
            }
        }
    }

    private fun parseOtherFeatures(json: JSONObject, features: MutableList<UniProtFeature>) {
        val bindingSiteString = json.optString("Binding site", "")
        if (bindingSiteString.isNotEmpty()) {
            parseFeatureString(bindingSiteString, FeatureType.BINDING_SITE, features)
        }

        val activeSiteString = json.optString("Active site", "")
        if (activeSiteString.isNotEmpty()) {
            parseFeatureString(activeSiteString, FeatureType.ACTIVE_SITE, features)
        }
    }

    private fun parseFeatureString(
        featureString: String,
        type: FeatureType,
        features: MutableList<UniProtFeature>
    ) {
        val parts = featureString.split(";")
        var start = -1
        var end = -1

        for (part in parts) {
            val trimmed = part.trim()
            val positions = Regex("(\\d+)\\.\\.(\\d+)|(\\d+)").find(trimmed)
            if (positions != null) {
                if (positions.groupValues[1].isNotEmpty()) {
                    start = positions.groupValues[1].toInt()
                    end = positions.groupValues[2].toInt()
                } else if (positions.groupValues[3].isNotEmpty()) {
                    start = positions.groupValues[3].toInt()
                    end = start
                }
            }

            if (trimmed.contains("/note=") && start > 0) {
                val noteMatch = Regex("\"(.+?)\"").find(trimmed)
                features.add(
                    UniProtFeature(
                        type = type,
                        startPosition = start,
                        endPosition = end,
                        description = noteMatch?.groupValues?.get(1) ?: type.name
                    )
                )
                start = -1
                end = -1
            }
        }
    }

    fun extractDomains(dataJson: String): List<ProteinDomain> {
        val domains = mutableListOf<ProteinDomain>()

        try {
            val json = JSONObject(dataJson)
            val domainString = json.optString("Domain [FT]", "")

            if (domainString.isNotEmpty()) {
                val parts = domainString.split(";")
                var start = -1
                var end = -1

                for (part in parts) {
                    val trimmed = part.trim()
                    when {
                        trimmed.contains("DOMAIN") -> {
                            val positions = Regex("(\\d+)").findAll(trimmed).toList()
                            if (positions.size >= 2) {
                                start = positions[0].value.toInt()
                                end = positions[1].value.toInt()
                            }
                        }
                        trimmed.contains("/note=") && start > 0 -> {
                            val noteMatch = Regex("\"(.+?)\"").find(trimmed)
                            if (noteMatch != null) {
                                domains.add(
                                    ProteinDomain(
                                        name = noteMatch.groupValues[1],
                                        startPosition = start,
                                        endPosition = end
                                    )
                                )
                                start = -1
                                end = -1
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SequenceAlignmentService", "Error extracting domains", e)
        }

        return domains
    }

    fun extractSequence(dataJson: String): String {
        return try {
            val json = JSONObject(dataJson)
            json.optString("Sequence", "")
        } catch (e: Exception) {
            ""
        }
    }

    fun extractGeneName(dataJson: String): String? {
        return try {
            val json = JSONObject(dataJson)
            val geneNames = json.optString("Gene Names", "")
            geneNames.split(";", " ").firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    fun extractProteinName(dataJson: String): String? {
        return try {
            val json = JSONObject(dataJson)
            json.optString("Protein names", "").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    fun extractOrganism(dataJson: String): String? {
        return try {
            val json = JSONObject(dataJson)
            json.optString("Organism", "").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    fun parseModifications(dataJson: String): List<ParsedModification> {
        val modifications = mutableListOf<ParsedModification>()

        try {
            val json = JSONObject(dataJson)
            val sequence = json.optString("Sequence", "")

            if (!json.has("Modified residue")) {
                android.util.Log.d("SequenceAlignmentService", "No Modified residue key found")
                return modifications
            }

            val modResValue = json.get("Modified residue")
            android.util.Log.d("SequenceAlignmentService", "Modified residue type: ${modResValue?.javaClass?.name}")

            when (modResValue) {
                is org.json.JSONArray -> {
                    android.util.Log.d("SequenceAlignmentService", "JSONArray length: ${modResValue.length()}")
                    for (i in 0 until modResValue.length()) {
                        val modObj = modResValue.optJSONObject(i) ?: continue

                        val positionValue = modObj.opt("position")
                        android.util.Log.d("SequenceAlignmentService", "Item $i positionValue: $positionValue (${positionValue?.javaClass?.name})")
                        val position = when (positionValue) {
                            is Int -> positionValue
                            is Long -> positionValue.toInt()
                            is Double -> positionValue.toInt()
                            is String -> positionValue.toIntOrNull() ?: -1
                            else -> -1
                        }

                        val residue = modObj.optString("residue", "?").firstOrNull() ?: '?'
                        val modType = modObj.optString("modType", "")
                        android.util.Log.d("SequenceAlignmentService", "Parsed: pos=$position, residue=$residue, modType=$modType")

                        if (position > 0 && modType.isNotEmpty()) {
                            modifications.add(
                                ParsedModification(
                                    position = position,
                                    residue = residue,
                                    modType = modType
                                )
                            )
                        }
                    }
                }
                is String -> {
                    if (modResValue.isNotEmpty() && sequence.isNotEmpty()) {
                        val parts = modResValue.split("; ")
                        var currentPosition = -1

                        for (part in parts) {
                            when {
                                part.startsWith("MOD_RES") -> {
                                    currentPosition = part.split(" ").getOrNull(1)?.toIntOrNull() ?: -1
                                }
                                part.contains("note=") && currentPosition > 0 -> {
                                    val noteMatch = Regex("\"(.+?)\"").find(part)
                                    if (noteMatch != null) {
                                        val modType = noteMatch.groupValues[1]
                                        val residue = sequence.getOrNull(currentPosition - 1) ?: '?'
                                        modifications.add(
                                            ParsedModification(
                                                position = currentPosition,
                                                residue = residue,
                                                modType = modType
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SequenceAlignmentService", "Error parsing modifications", e)
        }

        return modifications
    }

    fun getAvailableModTypes(modifications: List<ParsedModification>): List<String> {
        return modifications.map { it.modType }.distinct().sorted()
    }

    fun comparePTMSites(
        experimentalSites: List<ExperimentalPTMSite>,
        uniprotFeatures: List<UniProtFeature>,
        canonicalSequence: String
    ): List<PTMSiteComparison> {
        val comparisons = mutableListOf<PTMSiteComparison>()
        val experimentalPositions = experimentalSites.associateBy { it.position }
        val uniprotPTMs = uniprotFeatures
            .filter { it.type == FeatureType.MODIFIED_RESIDUE }
            .associateBy { it.startPosition }

        val allPositions = (experimentalPositions.keys + uniprotPTMs.keys).distinct().sorted()

        for (position in allPositions) {
            val experimental = experimentalPositions[position]
            val uniprot = uniprotPTMs[position]
            val residue = canonicalSequence.getOrNull(position - 1) ?: '?'

            comparisons.add(
                PTMSiteComparison(
                    position = position,
                    residue = residue,
                    isExperimental = experimental != null,
                    isKnownUniprot = uniprot != null,
                    experimentalData = experimental,
                    uniprotFeature = uniprot
                )
            )
        }

        return comparisons
    }

    fun createAlignedPeptide(
        primaryId: String,
        peptideSequence: String?,
        positionString: String?,
        canonicalSequence: String,
        isSignificant: Boolean
    ): AlignedPeptide? {
        if (peptideSequence.isNullOrEmpty()) return null

        val alignment = alignPeptideToSequence(peptideSequence, canonicalSequence) ?: return null
        val ptmPositions = extractPTMPositionFromPeptide(peptideSequence, positionString, alignment.first)

        return AlignedPeptide(
            peptideSequence = cleanPeptideSequence(peptideSequence),
            startPosition = alignment.first,
            endPosition = alignment.second,
            ptmPositions = ptmPositions,
            primaryId = primaryId,
            isSignificant = isSignificant
        )
    }

    fun extractAvailableIsoforms(dataJson: String): List<String> {
        val isoforms = mutableListOf<String>()

        try {
            val json = JSONObject(dataJson)
            val altProducts = json.optString("Alternative products (isoforms)", "")

            if (altProducts.isNotEmpty()) {
                val parts = altProducts.split(Regex("[; ]"))
                for (part in parts) {
                    if (part.startsWith("IsoId=")) {
                        val isoId = part.removePrefix("IsoId=").trim()
                        if (isoId.isNotEmpty()) {
                            isoforms.add(isoId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SequenceAlignmentService", "Error extracting isoforms", e)
        }

        return isoforms
    }
}
