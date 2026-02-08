package info.proteo.curtain

import info.proteo.curtain.domain.model.AdvancedFilterParams
import info.proteo.curtain.domain.model.SearchMatchType
import info.proteo.curtain.domain.model.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProteinSearchServiceTest {

    private fun parseSearchInput(input: String, useRegex: Boolean): List<String> {
        return input.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { line ->
                if (!useRegex && line.contains(";")) {
                    line.split(";").map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    listOf(line)
                }
            }
    }

    private fun matchesAdvancedFilter(
        fc: Double?,
        p: Double?,
        params: AdvancedFilterParams,
        pCutoff: Double
    ): Boolean {
        if (fc == null || p == null) return false

        val pValueInRange = p >= params.minP && p <= params.maxP

        if (!params.searchLeft && !params.searchRight) {
            return pValueInRange
        }

        val matchesLeft = if (params.searchLeft) {
            fc >= -params.maxFCLeft && fc <= -params.minFCLeft
        } else {
            false
        }

        val matchesRight = if (params.searchRight) {
            fc >= params.minFCRight && fc <= params.maxFCRight
        } else {
            false
        }

        return pValueInRange && (matchesLeft || matchesRight)
    }

    private fun findMatch(
        proteinId: String,
        geneName: String?,
        query: String,
        searchQuery: SearchQuery
    ): SearchMatchType? {
        val proteinIdToSearch = if (searchQuery.caseSensitive) proteinId else proteinId.lowercase()
        val geneNameToSearch = if (searchQuery.caseSensitive) geneName else geneName?.lowercase()
        val queryToUse = if (searchQuery.caseSensitive) query else query.lowercase()

        if (searchQuery.useRegex) {
            return try {
                val regex = Regex(queryToUse, if (searchQuery.caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE))

                when {
                    searchQuery.searchInProteinIds && regex.matches(proteinIdToSearch) -> SearchMatchType.REGEX_MATCH
                    searchQuery.searchInGeneNames && geneNameToSearch != null && regex.matches(geneNameToSearch) -> SearchMatchType.REGEX_MATCH
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }

        if (searchQuery.exactMatch) {
            return when {
                searchQuery.searchInProteinIds && proteinIdToSearch == queryToUse -> SearchMatchType.EXACT_PROTEIN_ID
                searchQuery.searchInGeneNames && geneNameToSearch == queryToUse -> SearchMatchType.EXACT_GENE_NAME
                else -> null
            }
        }

        return when {
            searchQuery.searchInProteinIds && proteinIdToSearch == queryToUse -> SearchMatchType.EXACT_PROTEIN_ID
            searchQuery.searchInGeneNames && geneNameToSearch == queryToUse -> SearchMatchType.EXACT_GENE_NAME
            searchQuery.searchInProteinIds && proteinIdToSearch.contains(queryToUse) -> SearchMatchType.CONTAINS_PROTEIN_ID
            searchQuery.searchInGeneNames && geneNameToSearch?.contains(queryToUse) == true -> SearchMatchType.CONTAINS_GENE_NAME
            else -> null
        }
    }

    @Test
    fun `parseSearchInput handles single term`() {
        val result = parseSearchInput("LRRK2", false)
        assertEquals(listOf("LRRK2"), result)
    }

    @Test
    fun `parseSearchInput handles newline separated terms`() {
        val result = parseSearchInput("LRRK2\nPINK1\nPRKN", false)
        assertEquals(listOf("LRRK2", "PINK1", "PRKN"), result)
    }

    @Test
    fun `parseSearchInput handles semicolon separated terms when not regex`() {
        val result = parseSearchInput("LRRK2;PINK1;PRKN", false)
        assertEquals(listOf("LRRK2", "PINK1", "PRKN"), result)
    }

    @Test
    fun `parseSearchInput does not split semicolons when regex enabled`() {
        val result = parseSearchInput("LRRK2;PINK1", true)
        assertEquals(listOf("LRRK2;PINK1"), result)
    }

    @Test
    fun `parseSearchInput trims whitespace`() {
        val result = parseSearchInput("  LRRK2  \n  PINK1  ", false)
        assertEquals(listOf("LRRK2", "PINK1"), result)
    }

    @Test
    fun `parseSearchInput removes empty lines`() {
        val result = parseSearchInput("LRRK2\n\n\nPINK1", false)
        assertEquals(listOf("LRRK2", "PINK1"), result)
    }

    @Test
    fun `parseSearchInput handles mixed separators`() {
        val result = parseSearchInput("LRRK2;PINK1\nPRKN;SNCA", false)
        assertEquals(listOf("LRRK2", "PINK1", "PRKN", "SNCA"), result)
    }

    @Test
    fun `matchesAdvancedFilter returns false for null values`() {
        val params = AdvancedFilterParams(
            searchLeft = true,
            searchRight = true,
            minFCLeft = 0.0,
            maxFCLeft = 10.0,
            minFCRight = 0.0,
            maxFCRight = 10.0,
            minP = 0.0,
            maxP = 0.05
        )
        assertFalse(matchesAdvancedFilter(null, 0.01, params, 0.05))
        assertFalse(matchesAdvancedFilter(1.5, null, params, 0.05))
        assertFalse(matchesAdvancedFilter(null, null, params, 0.05))
    }

    @Test
    fun `matchesAdvancedFilter filters by p-value range`() {
        val params = AdvancedFilterParams(
            searchLeft = false,
            searchRight = false,
            minFCLeft = 0.0,
            maxFCLeft = 0.0,
            minFCRight = 0.0,
            maxFCRight = 0.0,
            minP = 0.001,
            maxP = 0.05
        )
        assertTrue(matchesAdvancedFilter(1.5, 0.01, params, 0.05))
        assertTrue(matchesAdvancedFilter(1.5, 0.001, params, 0.05))
        assertTrue(matchesAdvancedFilter(1.5, 0.05, params, 0.05))
        assertFalse(matchesAdvancedFilter(1.5, 0.0001, params, 0.05))
        assertFalse(matchesAdvancedFilter(1.5, 0.1, params, 0.05))
    }

    @Test
    fun `matchesAdvancedFilter filters by left side FC`() {
        val params = AdvancedFilterParams(
            searchLeft = true,
            searchRight = false,
            minFCLeft = 1.0,
            maxFCLeft = 3.0,
            minFCRight = 0.0,
            maxFCRight = 0.0,
            minP = 0.0,
            maxP = 0.05
        )
        assertTrue(matchesAdvancedFilter(-1.5, 0.01, params, 0.05))
        assertTrue(matchesAdvancedFilter(-2.5, 0.01, params, 0.05))
        assertFalse(matchesAdvancedFilter(-0.5, 0.01, params, 0.05))
        assertFalse(matchesAdvancedFilter(-4.0, 0.01, params, 0.05))
        assertFalse(matchesAdvancedFilter(1.5, 0.01, params, 0.05))
    }

    @Test
    fun `matchesAdvancedFilter filters by right side FC`() {
        val params = AdvancedFilterParams(
            searchLeft = false,
            searchRight = true,
            minFCLeft = 0.0,
            maxFCLeft = 0.0,
            minFCRight = 1.0,
            maxFCRight = 3.0,
            minP = 0.0,
            maxP = 0.05
        )
        assertTrue(matchesAdvancedFilter(1.5, 0.01, params, 0.05))
        assertTrue(matchesAdvancedFilter(2.5, 0.01, params, 0.05))
        assertFalse(matchesAdvancedFilter(0.5, 0.01, params, 0.05))
        assertFalse(matchesAdvancedFilter(4.0, 0.01, params, 0.05))
        assertFalse(matchesAdvancedFilter(-1.5, 0.01, params, 0.05))
    }

    @Test
    fun `matchesAdvancedFilter filters by both sides (OR logic)`() {
        val params = AdvancedFilterParams(
            searchLeft = true,
            searchRight = true,
            minFCLeft = 1.0,
            maxFCLeft = 2.0,
            minFCRight = 1.0,
            maxFCRight = 2.0,
            minP = 0.0,
            maxP = 0.05
        )
        assertTrue(matchesAdvancedFilter(-1.5, 0.01, params, 0.05))
        assertTrue(matchesAdvancedFilter(1.5, 0.01, params, 0.05))
        assertFalse(matchesAdvancedFilter(0.5, 0.01, params, 0.05))
        assertFalse(matchesAdvancedFilter(-0.5, 0.01, params, 0.05))
    }

    @Test
    fun `findMatch exact match on protein ID`() {
        val query = SearchQuery(
            query = "P12345",
            searchInProteinIds = true,
            searchInGeneNames = false,
            exactMatch = true,
            caseSensitive = false,
            useRegex = false
        )
        val result = findMatch("P12345", "LRRK2", "P12345", query)
        assertEquals(SearchMatchType.EXACT_PROTEIN_ID, result)
    }

    @Test
    fun `findMatch exact match on gene name`() {
        val query = SearchQuery(
            query = "LRRK2",
            searchInProteinIds = false,
            searchInGeneNames = true,
            exactMatch = true,
            caseSensitive = false,
            useRegex = false
        )
        val result = findMatch("Q5S007", "LRRK2", "lrrk2", query)
        assertEquals(SearchMatchType.EXACT_GENE_NAME, result)
    }

    @Test
    fun `findMatch contains match on protein ID`() {
        val query = SearchQuery(
            query = "123",
            searchInProteinIds = true,
            searchInGeneNames = false,
            exactMatch = false,
            caseSensitive = false,
            useRegex = false
        )
        val result = findMatch("P12345", "LRRK2", "123", query)
        assertEquals(SearchMatchType.CONTAINS_PROTEIN_ID, result)
    }

    @Test
    fun `findMatch contains match on gene name`() {
        val query = SearchQuery(
            query = "RRK",
            searchInProteinIds = false,
            searchInGeneNames = true,
            exactMatch = false,
            caseSensitive = false,
            useRegex = false
        )
        val result = findMatch("Q5S007", "LRRK2", "rrk", query)
        assertEquals(SearchMatchType.CONTAINS_GENE_NAME, result)
    }

    @Test
    fun `findMatch regex match on protein ID`() {
        val query = SearchQuery(
            query = "P\\d+",
            searchInProteinIds = true,
            searchInGeneNames = false,
            exactMatch = false,
            caseSensitive = false,
            useRegex = true
        )
        val result = findMatch("P12345", "LRRK2", "P\\d+", query)
        assertEquals(SearchMatchType.REGEX_MATCH, result)
    }

    @Test
    fun `findMatch regex match on gene name`() {
        val query = SearchQuery(
            query = "LRRK\\d",
            searchInProteinIds = false,
            searchInGeneNames = true,
            exactMatch = false,
            caseSensitive = false,
            useRegex = true
        )
        val result = findMatch("Q5S007", "LRRK2", "lrrk\\d", query)
        assertEquals(SearchMatchType.REGEX_MATCH, result)
    }

    @Test
    fun `findMatch case sensitive exact match`() {
        val query = SearchQuery(
            query = "LRRK2",
            searchInProteinIds = false,
            searchInGeneNames = true,
            exactMatch = true,
            caseSensitive = true,
            useRegex = false
        )
        val result = findMatch("Q5S007", "LRRK2", "LRRK2", query)
        assertEquals(SearchMatchType.EXACT_GENE_NAME, result)

        val noMatchResult = findMatch("Q5S007", "lrrk2", "LRRK2", query)
        assertNull(noMatchResult)
    }

    @Test
    fun `findMatch returns null when no match`() {
        val query = SearchQuery(
            query = "MAPK1",
            searchInProteinIds = true,
            searchInGeneNames = true,
            exactMatch = true,
            caseSensitive = false,
            useRegex = false
        )
        val result = findMatch("Q5S007", "LRRK2", "mapk1", query)
        assertNull(result)
    }

    @Test
    fun `findMatch handles null gene name`() {
        val query = SearchQuery(
            query = "LRRK2",
            searchInProteinIds = true,
            searchInGeneNames = true,
            exactMatch = true,
            caseSensitive = false,
            useRegex = false
        )
        val result = findMatch("Q5S007", null, "lrrk2", query)
        assertNull(result)
    }

    @Test
    fun `findMatch invalid regex returns null`() {
        val query = SearchQuery(
            query = "[invalid",
            searchInProteinIds = true,
            searchInGeneNames = false,
            exactMatch = false,
            caseSensitive = false,
            useRegex = true
        )
        val result = findMatch("P12345", "LRRK2", "[invalid", query)
        assertNull(result)
    }

    @Test
    fun `findMatch prefers exact over contains`() {
        val query = SearchQuery(
            query = "LRRK2",
            searchInProteinIds = false,
            searchInGeneNames = true,
            exactMatch = false,
            caseSensitive = false,
            useRegex = false
        )
        val result = findMatch("Q5S007", "LRRK2", "lrrk2", query)
        assertEquals(SearchMatchType.EXACT_GENE_NAME, result)
    }
}
