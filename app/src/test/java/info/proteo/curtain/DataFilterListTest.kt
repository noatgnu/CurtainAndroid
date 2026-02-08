package info.proteo.curtain

import info.proteo.curtain.data.local.entity.DataFilterListEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataFilterListTest {

    private lateinit var testFilters: List<DataFilterListEntity>

    companion object {
        const val AMPK_COMPLEX_DATA = "PRKAA1\nPRKAA2\nPRKAG1\nPRKAG2\nPRKAG3\nPRKAB1\nPRKAB2"

        const val AGGREGPHAGY_DATA = "ALFY\nCCT2\nNBR1\nOPTN\nSQSTM1\nTAX1BP1\nTOLLIP\nUBQLN2"

        const val APOPTOSIS_DATA = "TRAF2\nTRADD\nRIPK1\nFADD\nCASP8\nCFLAR\nTNFRSF10B\nTNFRSF10A\nTNFSF10\nFASLG\nFAS\nCD14\nTLR4\nBAX\nBCL2\nCASP3\nCASP9\nTP53"

        const val AUTOPHAGY_REGULATORS_DATA = "ATF2\nATF3\nATF4\nATF6\nCEBPB\nCREB1\nFOXO1\nFOXO3\nHIF1A\nMAPK1\nMAPK3\nNFE2L2\nSIRT1\nTFEB\nTP53"

        const val ALS_GENES_DATA = "SOD1\nTARDBP\nFUS\nC9orf72\nOPTN\nVCP\nUBQLN2\nSQSTM1\nTBK1\nNEK1"
    }

    @Before
    fun setUp() {
        testFilters = listOf(
            DataFilterListEntity(
                id = 1,
                apiId = 58,
                name = "AMPK Complex",
                category = "Enzyme",
                data = AMPK_COMPLEX_DATA,
                isDefault = true,
                user = null
            ),
            DataFilterListEntity(
                id = 2,
                apiId = 36,
                name = "Aggregphagy",
                category = "Pathway",
                data = AGGREGPHAGY_DATA,
                isDefault = true,
                user = null
            ),
            DataFilterListEntity(
                id = 3,
                apiId = 142,
                name = "Apoptosis",
                category = "Pathway",
                data = APOPTOSIS_DATA,
                isDefault = true,
                user = null
            ),
            DataFilterListEntity(
                id = 4,
                apiId = 64,
                name = "Autophagy regulators",
                category = "Pathway",
                data = AUTOPHAGY_REGULATORS_DATA,
                isDefault = true,
                user = null
            ),
            DataFilterListEntity(
                id = 5,
                apiId = 24,
                name = "Amyotrophic Lateral Sclerosis (ALS)",
                category = "Disease",
                data = ALS_GENES_DATA,
                isDefault = true,
                user = null
            ),
            DataFilterListEntity(
                id = 6,
                apiId = 999,
                name = "My Custom List",
                category = "User's lists",
                data = "GENE1\nGENE2\nGENE3",
                isDefault = false,
                user = 1
            )
        )
    }

    private fun getFilteredList(
        filters: List<DataFilterListEntity>,
        selectedCategory: String,
        searchQuery: String
    ): List<DataFilterListEntity> {
        var filtered = if (selectedCategory == "All") {
            filters
        } else {
            filters.filter { it.category == selectedCategory }
        }

        if (searchQuery.isNotEmpty()) {
            val queryLower = searchQuery.lowercase()
            filtered = filtered.filter {
                it.name.lowercase().contains(queryLower) ||
                it.category.lowercase().contains(queryLower) ||
                it.data.lowercase().contains(queryLower)
            }
        }

        return filtered
    }

    private fun getCategories(filters: List<DataFilterListEntity>): List<String> {
        val categories = filters.map { it.category }.distinct().sorted()
        return listOf("All") + categories
    }

    private fun parseFilterData(data: String): List<String> {
        return data.split("\n", "\r\n", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    @Test
    fun `getFilteredList returns all filters when category is All`() {
        val result = getFilteredList(testFilters, "All", "")
        assertEquals(6, result.size)
    }

    @Test
    fun `getFilteredList filters by Pathway category`() {
        val result = getFilteredList(testFilters, "Pathway", "")
        assertEquals(3, result.size)
        assertTrue(result.all { it.category == "Pathway" })
        assertTrue(result.any { it.name == "Aggregphagy" })
        assertTrue(result.any { it.name == "Apoptosis" })
        assertTrue(result.any { it.name == "Autophagy regulators" })
    }

    @Test
    fun `getFilteredList filters by Disease category`() {
        val result = getFilteredList(testFilters, "Disease", "")
        assertEquals(1, result.size)
        assertEquals("Amyotrophic Lateral Sclerosis (ALS)", result[0].name)
    }

    @Test
    fun `getFilteredList filters by search query in name`() {
        val result = getFilteredList(testFilters, "All", "AMPK")
        assertEquals(1, result.size)
        assertEquals("AMPK Complex", result[0].name)
    }

    @Test
    fun `getFilteredList filters by search query in category`() {
        val result = getFilteredList(testFilters, "All", "enzyme")
        assertEquals(1, result.size)
        assertEquals("AMPK Complex", result[0].name)
    }

    @Test
    fun `getFilteredList filters by search query in data - finds SOD1`() {
        val result = getFilteredList(testFilters, "All", "SOD1")
        assertEquals(1, result.size)
        assertEquals("Amyotrophic Lateral Sclerosis (ALS)", result[0].name)
    }

    @Test
    fun `getFilteredList filters by search query in data - finds TP53`() {
        val result = getFilteredList(testFilters, "All", "TP53")
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Apoptosis" })
        assertTrue(result.any { it.name == "Autophagy regulators" })
    }

    @Test
    fun `getFilteredList combines category and search filters`() {
        val result = getFilteredList(testFilters, "Pathway", "autophagy")
        assertEquals(1, result.size)
        assertEquals("Autophagy regulators", result[0].name)
    }

    @Test
    fun `getFilteredList returns empty for non-matching query`() {
        val result = getFilteredList(testFilters, "All", "nonexistent")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getFilteredList is case insensitive`() {
        val result = getFilteredList(testFilters, "All", "ampk")
        assertEquals(1, result.size)
    }

    @Test
    fun `getCategories returns All plus sorted categories`() {
        val categories = getCategories(testFilters)
        assertEquals(listOf("All", "Disease", "Enzyme", "Pathway", "User's lists"), categories)
    }

    @Test
    fun `getCategories handles empty list`() {
        val categories = getCategories(emptyList())
        assertEquals(listOf("All"), categories)
    }

    @Test
    fun `parseFilterData splits newline separated data`() {
        val result = parseFilterData("PRKAA1\nPRKAA2\nPRKAG1")
        assertEquals(listOf("PRKAA1", "PRKAA2", "PRKAG1"), result)
    }

    @Test
    fun `parseFilterData handles Windows line endings`() {
        val result = parseFilterData("PRKAA1\r\nPRKAA2\r\nPRKAG1")
        assertEquals(listOf("PRKAA1", "PRKAA2", "PRKAG1"), result)
    }

    @Test
    fun `parseFilterData handles semicolon separated data`() {
        val result = parseFilterData("GENE1;GENE2;GENE3")
        assertEquals(listOf("GENE1", "GENE2", "GENE3"), result)
    }

    @Test
    fun `parseFilterData trims whitespace`() {
        val result = parseFilterData(" PRKAA1 \n PRKAA2 \n PRKAG1 ")
        assertEquals(listOf("PRKAA1", "PRKAA2", "PRKAG1"), result)
    }

    @Test
    fun `parseFilterData removes empty entries`() {
        val result = parseFilterData("PRKAA1\n\n\nPRKAA2")
        assertEquals(listOf("PRKAA1", "PRKAA2"), result)
    }

    @Test
    fun `parseFilterData handles single entry`() {
        val result = parseFilterData("PRKAA1")
        assertEquals(listOf("PRKAA1"), result)
    }

    @Test
    fun `filter entity has correct default values`() {
        val defaultFilter = testFilters.find { it.name == "AMPK Complex" }!!
        assertTrue(defaultFilter.isDefault)
        assertEquals(null, defaultFilter.user)
        assertEquals(58, defaultFilter.apiId)
    }

    @Test
    fun `filter entity has correct user filter values`() {
        val userFilter = testFilters.find { it.name == "My Custom List" }!!
        assertEquals(false, userFilter.isDefault)
        assertEquals(1, userFilter.user)
    }

    @Test
    fun `AMPK Complex filter contains expected subunits`() {
        val ampkFilter = testFilters.find { it.name == "AMPK Complex" }!!
        val genes = parseFilterData(ampkFilter.data)
        assertEquals(7, genes.size)
        assertTrue(genes.contains("PRKAA1"))
        assertTrue(genes.contains("PRKAA2"))
        assertTrue(genes.contains("PRKAB1"))
        assertTrue(genes.contains("PRKAG1"))
    }

    @Test
    fun `Apoptosis filter contains key apoptosis genes`() {
        val apoptosisFilter = testFilters.find { it.name == "Apoptosis" }!!
        val genes = parseFilterData(apoptosisFilter.data)
        assertTrue(genes.contains("CASP3"))
        assertTrue(genes.contains("CASP8"))
        assertTrue(genes.contains("CASP9"))
        assertTrue(genes.contains("BAX"))
        assertTrue(genes.contains("BCL2"))
        assertTrue(genes.contains("TP53"))
    }

    @Test
    fun `ALS filter contains established ALS genes`() {
        val alsFilter = testFilters.find { it.name == "Amyotrophic Lateral Sclerosis (ALS)" }!!
        val genes = parseFilterData(alsFilter.data)
        assertTrue(genes.contains("SOD1"))
        assertTrue(genes.contains("TARDBP"))
        assertTrue(genes.contains("FUS"))
        assertTrue(genes.contains("C9orf72"))
        assertTrue(genes.contains("TBK1"))
    }

    @Test
    fun `multiple filters can have overlapping genes`() {
        val aggregphagyGenes = parseFilterData(testFilters.find { it.name == "Aggregphagy" }!!.data)
        val alsGenes = parseFilterData(testFilters.find { it.name == "Amyotrophic Lateral Sclerosis (ALS)" }!!.data)

        val overlap = aggregphagyGenes.intersect(alsGenes.toSet())
        assertTrue(overlap.contains("OPTN"))
        assertTrue(overlap.contains("SQSTM1"))
        assertTrue(overlap.contains("UBQLN2"))
    }

    @Test
    fun `Autophagy regulators and Apoptosis share TP53`() {
        val autophagyGenes = parseFilterData(testFilters.find { it.name == "Autophagy regulators" }!!.data)
        val apoptosisGenes = parseFilterData(testFilters.find { it.name == "Apoptosis" }!!.data)

        val overlap = autophagyGenes.intersect(apoptosisGenes.toSet())
        assertTrue(overlap.contains("TP53"))
    }

    @Test
    fun `search for SQSTM1 finds both Aggregphagy and ALS`() {
        val result = getFilteredList(testFilters, "All", "SQSTM1")
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Aggregphagy" })
        assertTrue(result.any { it.name == "Amyotrophic Lateral Sclerosis (ALS)" })
    }

    @Test
    fun `User lists category contains custom list`() {
        val result = getFilteredList(testFilters, "User's lists", "")
        assertEquals(1, result.size)
        assertEquals("My Custom List", result[0].name)
        assertEquals(false, result[0].isDefault)
    }
}
