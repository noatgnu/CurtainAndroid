package info.proteo.curtain.domain.model

data class PTMViewerState(
    val accession: String,
    val geneName: String?,
    val proteinName: String?,
    val organism: String?,
    val canonicalSequence: String,
    val sequenceLength: Int,
    val experimentalSites: List<ExperimentalPTMSite>,
    val uniprotFeatures: List<UniProtFeature>,
    val alignedPeptides: List<AlignedPeptide>,
    val domains: List<ProteinDomain>,
    val alignedSequencePair: AlignedSequencePair? = null,
    val experimentalSequenceSource: String? = null,
    val parsedModifications: List<ParsedModification> = emptyList(),
    val availableModTypes: List<String> = emptyList(),
    val availableIsoforms: List<String> = emptyList(),
    val selectedVariant: String? = null,
    val customSequence: String? = null,
    val customPTMSites: Map<String, List<CustomPTMSite>> = emptyMap(),
    val availableCustomDatabases: List<String> = emptyList(),
    val selectedCustomDatabases: Set<String> = emptySet()
)

data class CustomPTMSite(
    val databaseName: String,
    val position: Int,
    val residue: String
)

data class ExperimentalPTMSite(
    val primaryId: String,
    val position: Int,
    val residue: Char,
    val modification: String?,
    val peptideSequence: String?,
    val foldChange: Double?,
    val pValue: Double?,
    val isSignificant: Boolean,
    val comparison: String?
)

data class UniProtFeature(
    val type: FeatureType,
    val startPosition: Int,
    val endPosition: Int,
    val description: String,
    val evidence: String? = null
)

enum class FeatureType {
    MODIFIED_RESIDUE,
    ACTIVE_SITE,
    BINDING_SITE,
    DOMAIN,
    REGION,
    MOTIF,
    SIGNAL_PEPTIDE,
    TRANSMEMBRANE,
    DISULFIDE_BOND,
    GLYCOSYLATION,
    LIPIDATION,
    OTHER
}

data class ProteinDomain(
    val name: String,
    val startPosition: Int,
    val endPosition: Int,
    val description: String? = null
)

data class AlignedPeptide(
    val peptideSequence: String,
    val startPosition: Int,
    val endPosition: Int,
    val ptmPositions: List<PTMPosition>,
    val primaryId: String,
    val isSignificant: Boolean
)

data class PTMPosition(
    val positionInPeptide: Int,
    val positionInProtein: Int,
    val residue: Char,
    val modification: String?
)

data class SequenceSegment(
    val startPosition: Int,
    val endPosition: Int,
    val sequence: String,
    val annotations: List<SegmentAnnotation>
)

data class SegmentAnnotation(
    val type: AnnotationType,
    val position: Int,
    val description: String,
    val color: Long?
)

enum class AnnotationType {
    EXPERIMENTAL_PTM,
    UNIPROT_PTM,
    MATCHED_PTM,
    DOMAIN_START,
    DOMAIN_END,
    PEPTIDE_COVERAGE
}

data class PTMSiteComparison(
    val position: Int,
    val residue: Char,
    val isExperimental: Boolean,
    val isKnownUniprot: Boolean,
    val experimentalData: ExperimentalPTMSite?,
    val uniprotFeature: UniProtFeature?
) {
    val comparisonType: PTMComparisonType
        get() = when {
            isExperimental && isKnownUniprot -> PTMComparisonType.MATCHED
            isExperimental && !isKnownUniprot -> PTMComparisonType.NOVEL
            !isExperimental && isKnownUniprot -> PTMComparisonType.KNOWN_ONLY
            else -> PTMComparisonType.NONE
        }
}

enum class PTMComparisonType {
    MATCHED,
    NOVEL,
    KNOWN_ONLY,
    NONE
}

data class AlignedSequencePair(
    val experimentalSequence: String,
    val canonicalSequence: String,
    val experimentalAligned: String,
    val canonicalAligned: String,
    val experimentalPositionMap: Map<Int, Int>,
    val canonicalPositionMap: Map<Int, Int>
)

data class ParsedModification(
    val position: Int,
    val residue: Char,
    val modType: String
)
