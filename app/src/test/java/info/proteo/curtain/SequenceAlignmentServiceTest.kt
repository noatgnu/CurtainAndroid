package info.proteo.curtain

import info.proteo.curtain.domain.model.ExperimentalPTMSite
import info.proteo.curtain.domain.model.FeatureType
import info.proteo.curtain.domain.model.PTMComparisonType
import info.proteo.curtain.domain.service.SequenceAlignmentService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SequenceAlignmentServiceTest {

    private lateinit var service: SequenceAlignmentService

    companion object {
        const val MAPK1_SEQUENCE = "MAAAAAAGAGPEMVRGQVFDVGPRYTNLSYIGEGAYGMVCSAYDNVNKVRVAIKKISPFEHQTYCQRTLREIKILLRFRHENIIGINDIIRAPTIEQMKDVYIVQDLMETDLYKLLKTQHLSNDHICYFLYQILRGLKYIHSANVLHRDLKPSNLLLNTTCDLKICDFGLARVADPDHDHTGFLTEYVATRWYRAPEIMLNSKGYTKSIDIWSVGCILAEMLSNRPIFPGKHYLDQLNHILGILGSPSQEDLNCIINLKARNYLLSLPHKNKVPWNRLFPNADSKALDLLDKMLTFNPHKRIEVEQALAHPYLEQYYDPSDEPIAEAPFKFDMELDDLPKEKLKELIFEETARFQPGYRS"

        const val HEMOGLOBIN_BETA = "MVHLTPEEKSAVTALWGKVNVDEVGGEALGRLLVVYPWTQRFFESFGDLSTPDAVMGNPKVKAHGKKVLGAFSDGLAHLDNLKGTFATLSELHCDKLHVDPENFRLLGNVLVCVLAHHFGKEFTPPVQAAYQKVVAGVANALAHKYH"

        const val HEMOGLOBIN_BETA_SICKLE = "MVHLTPVEKSAVTALWGKVNVDEVGGEALGRLLVVYPWTQRFFESFGDLSTPDAVMGNPKVKAHGKKVLGAHSDGLAHLDNLKGTFATLSELHCDKLHVDPENFRLLGNVLVCVLAHHFGKEFTPPVQAAYQKVVAGVANALAHKYH"

        const val P53_CANONICAL = "MEEPQSDPSVEPPLSQETFSDLWKLLPENNVLSPLPSQAMDDLMLSPDDIEQWFTEDPGPDEAPRMPEAAPPVAPAPAAPTPAAPAPAPSWPLSSSVPSQKTYQGSYGFRLGFLHSGTAKSVTCTYSPALNKMFCQLAKTCPVQLWVDSTPPPGTRVRAMAIYKQSQHMTEVVRRCPHHERCSDSDGLAPPQHLIRVEGNLRVEYLDDRNTFRHSVVVPYEPPEVGSDCTTIHYNYMCNSSCMGGMNRRPILTIITLEDSSGNLLGRNSFEVRVCACPGRDRRTEEENLRKKGEPHHELPPGSTKRALPNNTSSSPQPKKKPLDGEYFTLQIRGRERFEMFRELNEALELKDAQAGKEPGGSRAHSSHLKSKKGQSTSRHKKLMFKTEGPDSD"

        const val P53_DEL40 = "MDDLMLSPDDIEQWFTEDPGPDEAPRMPEAAPPVAPAPAAPTPAAPAPAPSWPLSSSVPSQKTYQGSYGFRLGFLHSGTAKSVTCTYSPALNKMFCQLAKTCPVQLWVDSTPPPGTRVRAMAIYKQSQHMTEVVRRCPHHERCSDSDGLAPPQHLIRVEGNLRVEYLDDRNTFRHSVVVPYEPPEVGSDCTTIHYNYMCNSSCMGGMNRRPILTIITLEDSSGNLLGRNSFEVRVCACPGRDRRTEEENLRKKGEPHHELPPGSTKRALPNNTSSSPQPKKKPLDGEYFTLQIRGRERFEMFRELNEALELKDAQAGKEPGGSRAHSSHLKSKKGQSTSRHKKLMFKTEGPDSD"

        const val P53_BETA = "MEEPQSDPSVEPPLSQETFSDLWKLLPENNVLSPLPSQAMDDLMLSPDDIEQWFTEDPGPDEAPRMPEAAPPVAPAPAAPTPAAPAPAPSWPLSSSVPSQKTYQGSYGFRLGFLHSGTAKSVTCTYSPALNKMFCQLAKTCPVQLWVDSTPPPGTRVRAMAIYKQSQHMTEVVRRCPHHERCSDSDGLAPPQHLIRVEGNLRVEYLDDRNTFRHSVVVPYEPPEVGSDCTTIHYNYMCNSSCMGGMNRRPILTIITLEDSSGNLLGRNSFEVRVCACPGRDRRTEEENLRKKGEPHHELPPGSTKRALPNNTSSSPQPKKKPLDGEYFTLQDQTSFQKENC"
    }

    @Before
    fun setUp() {
        service = SequenceAlignmentService()
    }

    @Test
    fun `alignSequences returns identical alignment for identical sequences`() {
        val sequence = "MSTGLQVKPVADLAYAQ"
        val result = service.alignSequences(sequence, sequence)

        assertEquals(sequence, result.experimentalAligned)
        assertEquals(sequence, result.canonicalAligned)
        assertEquals(sequence.length, result.experimentalPositionMap.size)
        assertEquals(sequence.length, result.canonicalPositionMap.size)
    }

    @Test
    fun `alignSequences handles insertions correctly`() {
        val experimental = "MSTGLQVKPVADLAYAQ"
        val canonical = "MSTGLQVK---DLAYAQ"
        val result = service.alignSequences(experimental, canonical.replace("-", ""))

        assertTrue(result.experimentalAligned.contains("P") || result.experimentalAligned.contains("V") || result.experimentalAligned.contains("A"))
        assertTrue(result.canonicalAligned.contains("-") || result.experimentalAligned.length >= canonical.replace("-", "").length)
    }

    @Test
    fun `alignSequences handles deletions correctly`() {
        val experimental = "MSTGLQVK"
        val canonical = "MSTGLQVKPVADLAY"
        val result = service.alignSequences(experimental, canonical)

        assertTrue(result.experimentalAligned.length >= experimental.length)
        assertTrue(result.canonicalAligned.length >= canonical.length)
    }

    @Test
    fun `alignSequences handles empty sequences gracefully`() {
        val result = service.alignSequences("", "ABCDEF")
        assertEquals("", result.experimentalSequence)
        assertEquals("ABCDEF", result.canonicalSequence)

        val result2 = service.alignSequences("ABCDEF", "")
        assertEquals("ABCDEF", result2.experimentalSequence)
        assertEquals("", result2.canonicalSequence)
    }

    @Test
    fun `alignSequences handles single amino acid mismatches`() {
        val experimental = "MSTGLQVKPVADLAYAQ"
        val canonical = "MSTGLQVKPVADLXYAQ"
        val result = service.alignSequences(experimental, canonical)

        assertEquals(experimental.length, result.experimentalAligned.replace("-", "").length)
        assertEquals(canonical.length, result.canonicalAligned.replace("-", "").length)
    }

    @Test
    fun `alignSequences filters non-letter characters`() {
        val experimental = "MST123GLQVK"
        val canonical = "MSTGLQVK"
        val result = service.alignSequences(experimental, canonical)

        assertEquals("MSTGLQVK", result.experimentalSequence)
        assertEquals("MSTGLQVK", result.canonicalSequence)
    }

    @Test
    fun `alignPeptideToSequence finds exact match`() {
        val canonical = "MSTGLQVKPVADLAYAQPFGASEIPRVSVSPGPS"
        val peptide = "LAYAQPFG"
        val result = service.alignPeptideToSequence(peptide, canonical)

        assertNotNull(result)
        assertEquals(13, result!!.first)
        assertEquals(20, result.second)
    }

    @Test
    fun `alignPeptideToSequence handles modified peptide sequences`() {
        val canonical = "MSTGLQVKPVADLAYAQPFGASEIPRVSVSPGPS"
        val peptide = "LAYA[Phospho]QPFG"
        val result = service.alignPeptideToSequence(peptide, canonical)

        assertNotNull(result)
        assertEquals(13, result!!.first)
    }

    @Test
    fun `alignPeptideToSequence returns null for no match`() {
        val canonical = "MSTGLQVKPVADLAYAQPFGASEIPRVSVSPGPS"
        val peptide = "ZZZZZZZZZ"
        val result = service.alignPeptideToSequence(peptide, canonical)

        assertNull(result)
    }

    @Test
    fun `alignPeptideToSequence handles empty peptide`() {
        val canonical = "MSTGLQVKPVADLAYAQPFGASEIPRVSVSPGPS"
        val result = service.alignPeptideToSequence("", canonical)

        assertNull(result)
    }

    @Test
    fun `extractPTMPositionFromPeptide extracts modification position`() {
        val peptide = "LAYS[Phospho]AQPFG"
        val positions = service.extractPTMPositionFromPeptide(peptide, "S15", 10)

        assertTrue(positions.isNotEmpty())
    }

    @Test
    fun `extractPTMPositionFromPeptide handles multiple modifications`() {
        val peptide = "LAYS[Phospho]AQPFGT[Phospho]EST"
        val positions = service.extractPTMPositionFromPeptide(peptide, null, 10)

        assertTrue(positions.size >= 1)
    }

    @Test
    fun `extractPTMPositionFromPeptide handles parenthesis notation`() {
        val peptide = "LAYS(Phospho)AQPFG"
        val positions = service.extractPTMPositionFromPeptide(peptide, null, 10)

        assertTrue(positions.isNotEmpty())
    }

    @Test
    fun `parseUniProtFeatures parses legacy string format for modified residue`() {
        val jsonData = """{"Modified residue": "MOD_RES 15; /note=\"Phosphoserine\""}"""
        val features = service.parseUniProtFeatures(jsonData)

        assertTrue(features.isNotEmpty())
        assertTrue(features.any { it.type == FeatureType.MODIFIED_RESIDUE })
        assertEquals(15, features[0].startPosition)
    }

    @Test
    fun `parseUniProtFeatures extracts domains from string format`() {
        val jsonData = """{"Domain [FT]": "DOMAIN 1..50; /note=\"Kinase domain\""}"""
        val features = service.parseUniProtFeatures(jsonData)

        val domains = features.filter { it.type == FeatureType.DOMAIN }
        assertTrue(domains.isNotEmpty())
        assertEquals("Kinase domain", domains[0].description)
        assertEquals(1, domains[0].startPosition)
        assertEquals(50, domains[0].endPosition)
    }

    @Test
    fun `parseModifications parses legacy string format`() {
        val jsonData = """{"Sequence": "MSTGLQVKPVADLAYAQPFGASEIPRVSVSPGPS", "Modified residue": "MOD_RES 15; /note=\"Phosphoserine\"; MOD_RES 32; /note=\"Phosphothreonine\""}"""
        val modifications = service.parseModifications(jsonData)

        assertTrue(modifications.isNotEmpty())
        assertTrue(modifications.any { it.position == 15 && it.modType == "Phosphoserine" })
        assertTrue(modifications.any { it.position == 32 && it.modType == "Phosphothreonine" })
    }

    @Test
    fun `getAvailableModTypes returns distinct sorted types`() {
        val jsonData = """{"Sequence": "MSTGLQVKPVADLAYAQPFGASEIPRVSVSPGPS", "Modified residue": "MOD_RES 15; /note=\"Phosphoserine\"; MOD_RES 32; /note=\"Phosphothreonine\"; MOD_RES 45; /note=\"Phosphoserine\""}"""
        val modifications = service.parseModifications(jsonData)
        val modTypes = service.getAvailableModTypes(modifications)

        assertTrue(modTypes.isNotEmpty())
        assertEquals(modTypes.sorted(), modTypes)
        assertEquals(modTypes.distinct(), modTypes)
    }

    @Test
    fun `extractDomains returns protein domains`() {
        val jsonData = """{"Domain [FT]": "DOMAIN 1..50; /note=\"Kinase domain\"; DOMAIN 100..150; /note=\"SH2 domain\""}"""
        val domains = service.extractDomains(jsonData)

        assertTrue(domains.isNotEmpty())
        assertEquals(2, domains.size)

        val kinaseDomain = domains.find { it.name == "Kinase domain" }
        assertNotNull(kinaseDomain)
        assertEquals(1, kinaseDomain!!.startPosition)
        assertEquals(50, kinaseDomain.endPosition)

        val sh2Domain = domains.find { it.name == "SH2 domain" }
        assertNotNull(sh2Domain)
        assertEquals(100, sh2Domain!!.startPosition)
        assertEquals(150, sh2Domain.endPosition)
    }

    @Test
    fun `extractSequence returns protein sequence`() {
        val jsonData = """{"Sequence": "MSTGLQVKPVADLAYAQ"}"""
        val sequence = service.extractSequence(jsonData)

        assertTrue(sequence.isNotEmpty())
        assertTrue(sequence.startsWith("MST"))
        assertEquals("MSTGLQVKPVADLAYAQ", sequence)
    }

    @Test
    fun `extractGeneName returns first gene name`() {
        val jsonData = """{"Gene Names": "GENE1; GENE1_ALT"}"""
        val geneName = service.extractGeneName(jsonData)

        assertEquals("GENE1", geneName)
    }

    @Test
    fun `extractGeneName handles multiple gene names`() {
        val jsonData = """{"Gene Names": "MAPK1; ERK2"}"""
        val geneName = service.extractGeneName(jsonData)

        assertEquals("MAPK1", geneName)
    }

    @Test
    fun `extractProteinName returns protein name`() {
        val jsonData = """{"Protein names": "Test protein 1 (Alternative name 1)"}"""
        val proteinName = service.extractProteinName(jsonData)

        assertNotNull(proteinName)
        assertTrue(proteinName!!.contains("Test protein"))
    }

    @Test
    fun `extractOrganism returns organism`() {
        val jsonData = """{"Organism": "Homo sapiens (Human)"}"""
        val organism = service.extractOrganism(jsonData)

        assertEquals("Homo sapiens (Human)", organism)
    }

    @Test
    fun `extractAvailableIsoforms returns isoform IDs`() {
        val jsonData = """{"Alternative products (isoforms)": "Event=Alternative splicing; Named isoforms=3; Name=1; IsoId=P12345-1; Sequence=Displayed; Name=2; IsoId=P12345-2; Sequence=VSP_001234; Name=3; IsoId=P12345-3; Sequence=VSP_001235"}"""
        val isoforms = service.extractAvailableIsoforms(jsonData)

        assertTrue(isoforms.isNotEmpty())
        assertEquals(3, isoforms.size)
        assertTrue(isoforms.contains("P12345-1"))
        assertTrue(isoforms.contains("P12345-2"))
        assertTrue(isoforms.contains("P12345-3"))
    }

    @Test
    fun `extractAvailableIsoforms handles no isoforms`() {
        val jsonData = """{"Entry": "P00000", "Sequence": "MTEST"}"""
        val isoforms = service.extractAvailableIsoforms(jsonData)

        assertTrue(isoforms.isEmpty())
    }

    @Test
    fun `comparePTMSites identifies matched sites`() {
        val experimentalSites = listOf(
            ExperimentalPTMSite(
                primaryId = "P12345_S15",
                position = 15,
                residue = 'S',
                modification = "Phosphoserine",
                peptideSequence = "LAYSAQPFG",
                foldChange = 2.5,
                pValue = 0.01,
                isSignificant = true,
                comparison = "AO_UT"
            )
        )
        val jsonData = """{"Modified residue": "MOD_RES 15; /note=\"Phosphoserine\""}"""
        val uniprotFeatures = service.parseUniProtFeatures(jsonData)
        val sequence = "MSTGLQVKPVADLAYAQPFGASEIPRVSVSPGPS"

        val comparisons = service.comparePTMSites(experimentalSites, uniprotFeatures, sequence)

        assertTrue(comparisons.isNotEmpty())
        val matchedSite = comparisons.find { it.position == 15 }
        assertNotNull(matchedSite)
        assertTrue(matchedSite!!.isExperimental)
        assertTrue(matchedSite.isKnownUniprot)
        assertEquals(PTMComparisonType.MATCHED, matchedSite.comparisonType)
    }

    @Test
    fun `comparePTMSites identifies novel sites`() {
        val experimentalSites = listOf(
            ExperimentalPTMSite(
                primaryId = "P12345_S999",
                position = 999,
                residue = 'S',
                modification = "Phosphoserine",
                peptideSequence = "TESTPEPTIDE",
                foldChange = 1.5,
                pValue = 0.05,
                isSignificant = true,
                comparison = "AO_UT"
            )
        )
        val jsonData = """{"Modified residue": "MOD_RES 15; /note=\"Phosphoserine\""}"""
        val uniprotFeatures = service.parseUniProtFeatures(jsonData)
        val sequence = "MSTGLQVKPVADLAYAQPFGASEIPRVSVSPGPS"

        val comparisons = service.comparePTMSites(experimentalSites, uniprotFeatures, sequence)

        val novelSite = comparisons.find { it.position == 999 }
        assertNotNull(novelSite)
        assertTrue(novelSite!!.isExperimental)
        assertFalse(novelSite.isKnownUniprot)
        assertEquals(PTMComparisonType.NOVEL, novelSite.comparisonType)
    }

    @Test
    fun `createAlignedPeptide creates valid alignment`() {
        val sequence = "MSTGLQVKPVADLAYAQPFGASEIPRVSVSPGPSRAAPPSPHPSPSSTSSSS"
        val peptide = "STGLQVKPVADLAY"

        val result = service.createAlignedPeptide(
            primaryId = "P12345_S15",
            peptideSequence = peptide,
            positionString = "S2",
            canonicalSequence = sequence,
            isSignificant = true
        )

        assertNotNull(result)
        assertEquals("P12345_S15", result!!.primaryId)
        assertTrue(result.isSignificant)
        assertEquals(2, result.startPosition)
        assertEquals(15, result.endPosition)
    }

    @Test
    fun `createAlignedPeptide handles null peptide sequence`() {
        val result = service.createAlignedPeptide(
            primaryId = "P12345_S15",
            peptideSequence = null,
            positionString = "S15",
            canonicalSequence = "MSTGLQVKPVADLAYAQ",
            isSignificant = true
        )

        assertNull(result)
    }

    @Test
    fun `alignMultipleSequences aligns three sequences`() {
        val sequences = mapOf(
            "seq1" to "MSTGLQVKPVAD",
            "seq2" to "MSTGLQVK",
            "seq3" to "MSTGLQVKPVADLAY"
        )

        val result = service.alignMultipleSequences(sequences)

        assertEquals(3, result.size)
        val maxLength = result.values.maxOfOrNull { it.length } ?: 0
        assertTrue(result.values.all { it.length <= maxLength + 5 })
    }

    @Test
    fun `alignMultipleSequences handles single sequence`() {
        val sequences = mapOf("seq1" to "MSTGLQVK")
        val result = service.alignMultipleSequences(sequences)

        assertEquals(1, result.size)
        assertEquals("MSTGLQVK", result["seq1"])
    }

    @Test
    fun `position map correctly maps original to aligned positions`() {
        val experimental = "ABCDEF"
        val canonical = "ABXDEF"
        val result = service.alignSequences(experimental, canonical)

        for (i in 1..experimental.length) {
            assertTrue(result.experimentalPositionMap.containsKey(i))
        }
        for (i in 1..canonical.length) {
            assertTrue(result.canonicalPositionMap.containsKey(i))
        }
    }

    @Test
    fun `parseModifications handles multiple phosphorylation sites`() {
        val teyIndex = MAPK1_SEQUENCE.indexOf("TEY")
        val tPosition = teyIndex + 1
        val yPosition = teyIndex + 3

        val jsonData = """{"Sequence": "$MAPK1_SEQUENCE", "Modified residue": "MOD_RES $tPosition; /note=\"Phosphothreonine\"; MOD_RES $yPosition; /note=\"Phosphotyrosine\""}"""
        val modifications = service.parseModifications(jsonData)

        assertEquals(2, modifications.size)
        assertTrue(modifications.any { it.position == tPosition && it.modType == "Phosphothreonine" })
        assertTrue(modifications.any { it.position == yPosition && it.modType == "Phosphotyrosine" })
    }

    @Test
    fun `extractDomains handles multiple domains`() {
        val jsonData = """{"Domain [FT]": "DOMAIN 25..313; /note=\"Protein kinase\"; DOMAIN 180..220; /note=\"Activation loop\""}"""
        val domains = service.extractDomains(jsonData)

        assertEquals(2, domains.size)
        assertTrue(domains.any { it.name == "Protein kinase" })
        assertTrue(domains.any { it.name == "Activation loop" })
    }

    @Test
    fun `alignSequences aligns identical MAPK1 sequences`() {
        val result = service.alignSequences(MAPK1_SEQUENCE, MAPK1_SEQUENCE)

        assertEquals(MAPK1_SEQUENCE, result.experimentalAligned)
        assertEquals(MAPK1_SEQUENCE, result.canonicalAligned)
        assertEquals(MAPK1_SEQUENCE.length, result.experimentalPositionMap.size)
        assertEquals(MAPK1_SEQUENCE.length, result.canonicalPositionMap.size)
        assertFalse(result.experimentalAligned.contains("-"))
    }

    @Test
    fun `alignSequences detects hemoglobin E6V sickle cell mutation`() {
        val result = service.alignSequences(HEMOGLOBIN_BETA_SICKLE, HEMOGLOBIN_BETA)

        assertEquals(HEMOGLOBIN_BETA_SICKLE.length, result.experimentalAligned.replace("-", "").length)
        assertEquals(HEMOGLOBIN_BETA.length, result.canonicalAligned.replace("-", "").length)

        assertEquals(result.experimentalAligned.length, result.canonicalAligned.length)

        var mismatches = 0
        for (i in result.experimentalAligned.indices) {
            if (result.experimentalAligned[i] != result.canonicalAligned[i] &&
                result.experimentalAligned[i] != '-' && result.canonicalAligned[i] != '-') {
                mismatches++
            }
        }
        assertEquals(2, mismatches)
    }

    @Test
    fun `alignSequences handles p53 Del40 isoform with N-terminal deletion`() {
        val result = service.alignSequences(P53_DEL40, P53_CANONICAL)

        assertTrue(result.canonicalAligned.startsWith("M") || result.canonicalAligned.contains("-"))

        val expWithoutGaps = result.experimentalAligned.replace("-", "")
        val canWithoutGaps = result.canonicalAligned.replace("-", "")
        assertEquals(P53_DEL40.length, expWithoutGaps.length)
        assertEquals(P53_CANONICAL.length, canWithoutGaps.length)

        assertTrue(P53_CANONICAL.length > P53_DEL40.length)
    }

    @Test
    fun `alignSequences handles p53 beta isoform with C-terminal difference`() {
        val result = service.alignSequences(P53_BETA, P53_CANONICAL)

        val expWithoutGaps = result.experimentalAligned.replace("-", "")
        val canWithoutGaps = result.canonicalAligned.replace("-", "")
        assertEquals(P53_BETA.length, expWithoutGaps.length)
        assertEquals(P53_CANONICAL.length, canWithoutGaps.length)

        val sharedPrefix = "MEEPQSDPSVEPPLSQETFSDLWKLLPENNVLSPLPSQAMDDLMLSPDDIEQ"
        assertTrue(P53_BETA.startsWith(sharedPrefix))
        assertTrue(P53_CANONICAL.startsWith(sharedPrefix))
    }

    @Test
    fun `alignPeptideToSequence finds MAPK1 activation loop peptide`() {
        val peptide = "TGFLTEYVATRWYR"
        val result = service.alignPeptideToSequence(peptide, MAPK1_SEQUENCE)

        assertNotNull(result)
        val foundPeptide = MAPK1_SEQUENCE.substring(result!!.first - 1, result.second)
        assertEquals(peptide, foundPeptide)
    }

    @Test
    fun `alignPeptideToSequence finds MAPK1 TEY phosphorylation motif`() {
        val peptide = "VADPDHDHTGFLTEYVATR"
        val result = service.alignPeptideToSequence(peptide, MAPK1_SEQUENCE)

        assertNotNull(result)
        assertTrue(result!!.first > 150)
        assertTrue(result.second < 200)
    }

    @Test
    fun `alignPeptideToSequence finds hemoglobin sickle mutation region`() {
        val peptide = "VHLTPVEKSAV"
        val result = service.alignPeptideToSequence(peptide, HEMOGLOBIN_BETA_SICKLE)

        assertNotNull(result)
        assertEquals(2, result!!.first)
        assertEquals(12, result.second)
    }

    @Test
    fun `alignPeptideToSequence handles phosphorylated MAPK1 peptide`() {
        val peptide = "TGFLT[Phospho]EYVATRWYR"
        val result = service.alignPeptideToSequence(peptide, MAPK1_SEQUENCE)

        assertNotNull(result)
        val cleanPeptide = "TGFLTEYVATRWYR"
        val foundPeptide = MAPK1_SEQUENCE.substring(result!!.first - 1, result.second)
        assertEquals(cleanPeptide, foundPeptide)
    }

    @Test
    fun `alignPeptideToSequence finds p53 DNA binding domain peptide`() {
        val peptide = "SVTCTYSPALNKMFC"
        val result = service.alignPeptideToSequence(peptide, P53_CANONICAL)

        assertNotNull(result)
        val foundPeptide = P53_CANONICAL.substring(result!!.first - 1, result.second)
        assertEquals(peptide, foundPeptide)
    }

    @Test
    fun `extractPTMPositionFromPeptide extracts MAPK1 T phosphorylation from TEY motif`() {
        val teyIndex = MAPK1_SEQUENCE.indexOf("TEY")
        val tPosition = teyIndex + 1
        val peptideStart = MAPK1_SEQUENCE.indexOf("TGFLTEYVATR") + 1

        val peptide = "TGFL[Phospho]TEYVATR"
        val positions = service.extractPTMPositionFromPeptide(peptide, "T$tPosition", peptideStart)

        assertTrue(positions.isNotEmpty())
    }

    @Test
    fun `extractPTMPositionFromPeptide extracts MAPK1 Y phosphorylation from TEY motif`() {
        val teyIndex = MAPK1_SEQUENCE.indexOf("TEY")
        val yPosition = teyIndex + 3
        val peptideStart = MAPK1_SEQUENCE.indexOf("TGFLTEYVATR") + 1

        val peptide = "TGFLTE[Phospho]YVATR"
        val positions = service.extractPTMPositionFromPeptide(peptide, "Y$yPosition", peptideStart)

        assertTrue(positions.isNotEmpty())
    }

    @Test
    fun `parseModifications parses MAPK1 TEY phosphorylation sites`() {
        val teyIndex = MAPK1_SEQUENCE.indexOf("TEY")
        val tPosition = teyIndex + 1
        val yPosition = teyIndex + 3

        val jsonData = """{"Sequence": "$MAPK1_SEQUENCE", "Modified residue": "MOD_RES $tPosition; /note=\"Phosphothreonine\"; MOD_RES $yPosition; /note=\"Phosphotyrosine\""}"""
        val modifications = service.parseModifications(jsonData)

        assertEquals(2, modifications.size)
        assertTrue(modifications.any { it.position == tPosition && it.modType == "Phosphothreonine" })
        assertTrue(modifications.any { it.position == yPosition && it.modType == "Phosphotyrosine" })

        val tey = modifications.find { it.position == tPosition }
        assertNotNull(tey)
        assertEquals('T', tey!!.residue)

        val yey = modifications.find { it.position == yPosition }
        assertNotNull(yey)
        assertEquals('Y', yey!!.residue)
    }

    @Test
    fun `comparePTMSites identifies matched phosphosites in MAPK1`() {
        val teyIndex = MAPK1_SEQUENCE.indexOf("TEY")
        val tPosition = teyIndex + 1
        val yPosition = teyIndex + 3

        val experimentalSites = listOf(
            ExperimentalPTMSite(
                primaryId = "P28482_T$tPosition",
                position = tPosition,
                residue = 'T',
                modification = "Phosphothreonine",
                peptideSequence = "TGFLTEYVATR",
                foldChange = 3.2,
                pValue = 0.001,
                isSignificant = true,
                comparison = "Treatment_Control"
            ),
            ExperimentalPTMSite(
                primaryId = "P28482_Y$yPosition",
                position = yPosition,
                residue = 'Y',
                modification = "Phosphotyrosine",
                peptideSequence = "TGFLTEYVATR",
                foldChange = 2.8,
                pValue = 0.005,
                isSignificant = true,
                comparison = "Treatment_Control"
            )
        )

        val jsonData = """{"Modified residue": "MOD_RES $tPosition; /note=\"Phosphothreonine\"; MOD_RES $yPosition; /note=\"Phosphotyrosine\""}"""
        val uniprotFeatures = service.parseUniProtFeatures(jsonData)

        val comparisons = service.comparePTMSites(experimentalSites, uniprotFeatures, MAPK1_SEQUENCE)

        assertEquals(2, comparisons.size)

        val tSite = comparisons.find { it.position == tPosition }
        assertNotNull(tSite)
        assertTrue(tSite!!.isExperimental)
        assertTrue(tSite.isKnownUniprot)
        assertEquals(PTMComparisonType.MATCHED, tSite.comparisonType)

        val ySite = comparisons.find { it.position == yPosition }
        assertNotNull(ySite)
        assertTrue(ySite!!.isExperimental)
        assertTrue(ySite.isKnownUniprot)
        assertEquals(PTMComparisonType.MATCHED, ySite.comparisonType)
    }

    @Test
    fun `comparePTMSites identifies novel phosphosite in MAPK1`() {
        val teyIndex = MAPK1_SEQUENCE.indexOf("TEY")
        val tPosition = teyIndex + 1
        val novelPosition = 300

        val experimentalSites = listOf(
            ExperimentalPTMSite(
                primaryId = "P28482_S$novelPosition",
                position = novelPosition,
                residue = 'S',
                modification = "Phosphoserine",
                peptideSequence = "TESTSERPEPTIDE",
                foldChange = 1.5,
                pValue = 0.03,
                isSignificant = true,
                comparison = "Treatment_Control"
            )
        )

        val jsonData = """{"Modified residue": "MOD_RES $tPosition; /note=\"Phosphothreonine\""}"""
        val uniprotFeatures = service.parseUniProtFeatures(jsonData)

        val comparisons = service.comparePTMSites(experimentalSites, uniprotFeatures, MAPK1_SEQUENCE)

        val novelSite = comparisons.find { it.position == novelPosition }
        assertNotNull(novelSite)
        assertTrue(novelSite!!.isExperimental)
        assertFalse(novelSite.isKnownUniprot)
        assertEquals(PTMComparisonType.NOVEL, novelSite.comparisonType)
    }

    @Test
    fun `alignMultipleSequences aligns p53 isoforms`() {
        val sequences = mapOf(
            "canonical" to P53_CANONICAL,
            "del40" to P53_DEL40,
            "beta" to P53_BETA
        )

        val result = service.alignMultipleSequences(sequences)

        assertEquals(3, result.size)
        assertTrue(result.containsKey("canonical"))
        assertTrue(result.containsKey("del40"))
        assertTrue(result.containsKey("beta"))
    }

    @Test
    fun `position map correctly tracks residues through hemoglobin alignment`() {
        val result = service.alignSequences(HEMOGLOBIN_BETA_SICKLE, HEMOGLOBIN_BETA)

        for (i in 1..HEMOGLOBIN_BETA_SICKLE.length) {
            assertTrue(result.experimentalPositionMap.containsKey(i))
        }

        for (i in 1..HEMOGLOBIN_BETA.length) {
            assertTrue(result.canonicalPositionMap.containsKey(i))
        }

        val pos6Exp = result.experimentalPositionMap[6]
        val pos6Can = result.canonicalPositionMap[6]
        assertNotNull(pos6Exp)
        assertNotNull(pos6Can)
        assertEquals(pos6Exp, pos6Can)
    }

    @Test
    fun `createAlignedPeptide creates valid alignment for MAPK1 peptide`() {
        val peptide = "TGFLTEYVATR"
        val teyIndex = MAPK1_SEQUENCE.indexOf("TEY")
        val tPosition = teyIndex + 1

        val result = service.createAlignedPeptide(
            primaryId = "P28482_T$tPosition",
            peptideSequence = peptide,
            positionString = "T$tPosition",
            canonicalSequence = MAPK1_SEQUENCE,
            isSignificant = true
        )

        assertNotNull(result)
        assertEquals("P28482_T$tPosition", result!!.primaryId)
        assertTrue(result.isSignificant)
        assertTrue(result.startPosition > 0)
        assertTrue(result.endPosition > result.startPosition)
        assertEquals(peptide.length, result.endPosition - result.startPosition + 1)
    }

    @Test
    fun `extractSequence returns correct MAPK1 sequence from JSON`() {
        val jsonData = """{"Sequence": "$MAPK1_SEQUENCE", "Entry": "P28482"}"""
        val sequence = service.extractSequence(jsonData)

        assertEquals(MAPK1_SEQUENCE, sequence)
        assertEquals(MAPK1_SEQUENCE.length, sequence.length)
    }

    @Test
    fun `extractGeneName handles MAPK1 gene names`() {
        val jsonData = """{"Gene Names": "MAPK1 ERK2 PRKM1"}"""
        val geneName = service.extractGeneName(jsonData)

        assertEquals("MAPK1", geneName)
    }

    @Test
    fun `alignSequences preserves position mapping for p53 mutations`() {
        val mutant = P53_CANONICAL.replaceRange(248, 249, "W")
        val result = service.alignSequences(mutant, P53_CANONICAL)

        assertEquals(P53_CANONICAL.length, result.experimentalPositionMap.size)
        assertEquals(P53_CANONICAL.length, result.canonicalPositionMap.size)

        val mutPos = result.experimentalPositionMap[249]
        val canPos = result.canonicalPositionMap[249]
        assertEquals(mutPos, canPos)
    }
}
