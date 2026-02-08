package info.proteo.curtain

import info.proteo.curtain.domain.model.CrossDatasetAdvancedFilterParams
import info.proteo.curtain.domain.model.MatrixCell
import info.proteo.curtain.domain.model.MatrixFilterOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatasetSearchServiceTest {

    private fun parseSearchInput(terms: List<String>): List<String> {
        return terms.flatMap { term ->
            val splitByNewline = term.split("\n", "\r\n", "\r")
            splitByNewline
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .flatMap { line ->
                    if (line.contains(";")) {
                        line.split(";").map { it.trim() }.filter { it.isNotEmpty() }
                    } else {
                        listOf(line)
                    }
                }
        }.distinct()
    }

    private fun passesFilters(
        avgFC: Double?,
        hasSignificant: Boolean,
        significantOnly: Boolean,
        advancedFiltering: CrossDatasetAdvancedFilterParams?
    ): Boolean {
        val passesAdvancedFilter = advancedFiltering?.let { params ->
            if (avgFC == null) return@let true

            val passesLeftFilter = if (params.searchLeft && avgFC < 0) {
                val absFC = kotlin.math.abs(avgFC)
                absFC >= params.minFCLeft && absFC <= params.maxFCLeft
            } else !params.searchLeft || avgFC >= 0

            val passesRightFilter = if (params.searchRight && avgFC > 0) {
                avgFC >= params.minFCRight && avgFC <= params.maxFCRight
            } else !params.searchRight || avgFC <= 0

            if (params.searchLeft && params.searchRight) {
                passesLeftFilter || passesRightFilter
            } else if (params.searchLeft) {
                passesLeftFilter
            } else if (params.searchRight) {
                passesRightFilter
            } else {
                true
            }
        } ?: true

        return (!significantOnly || hasSignificant) && passesAdvancedFilter
    }

    private fun passesMatrixFilter(cell: MatrixCell, options: MatrixFilterOptions): Boolean {
        if (!cell.found) return !options.hideNotFound
        if (options.showSignificantOnly && !cell.isSignificant) return false
        if (options.minFoldChange != null && cell.foldChange != null) {
            if (kotlin.math.abs(cell.foldChange) < options.minFoldChange) return false
        }
        if (options.maxPValue != null && cell.pValue != null) {
            if (cell.pValue > options.maxPValue) return false
        }
        return true
    }

    private fun escapeCSV(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    @Test
    fun `parseSearchInput handles single term`() {
        val result = parseSearchInput(listOf("MAPK1"))
        assertEquals(listOf("MAPK1"), result)
    }

    @Test
    fun `parseSearchInput handles newline separated terms`() {
        val result = parseSearchInput(listOf("MAPK1\nMAPK3\nAKT1"))
        assertEquals(listOf("MAPK1", "MAPK3", "AKT1"), result)
    }

    @Test
    fun `parseSearchInput handles semicolon separated terms`() {
        val result = parseSearchInput(listOf("MAPK1;MAPK3;AKT1"))
        assertEquals(listOf("MAPK1", "MAPK3", "AKT1"), result)
    }

    @Test
    fun `parseSearchInput handles mixed separators`() {
        val result = parseSearchInput(listOf("MAPK1;MAPK3\nAKT1;AKT2"))
        assertEquals(listOf("MAPK1", "MAPK3", "AKT1", "AKT2"), result)
    }

    @Test
    fun `parseSearchInput trims whitespace`() {
        val result = parseSearchInput(listOf("  MAPK1  \n  MAPK3  "))
        assertEquals(listOf("MAPK1", "MAPK3"), result)
    }

    @Test
    fun `parseSearchInput removes empty terms`() {
        val result = parseSearchInput(listOf("MAPK1\n\n\nMAPK3"))
        assertEquals(listOf("MAPK1", "MAPK3"), result)
    }

    @Test
    fun `parseSearchInput removes duplicates`() {
        val result = parseSearchInput(listOf("MAPK1\nMAPK1\nMAPK3"))
        assertEquals(listOf("MAPK1", "MAPK3"), result)
    }

    @Test
    fun `parseSearchInput handles Windows line endings`() {
        val result = parseSearchInput(listOf("MAPK1\r\nMAPK3\r\nAKT1"))
        assertEquals(listOf("MAPK1", "MAPK3", "AKT1"), result)
    }

    @Test
    fun `parseSearchInput handles carriage return only`() {
        val result = parseSearchInput(listOf("MAPK1\rMAPK3"))
        assertEquals(listOf("MAPK1", "MAPK3"), result)
    }

    @Test
    fun `passesFilters allows all when no filtering`() {
        assertTrue(passesFilters(1.5, false, false, null))
        assertTrue(passesFilters(-1.5, false, false, null))
        assertTrue(passesFilters(null, false, false, null))
    }

    @Test
    fun `passesFilters filters by significantOnly`() {
        assertTrue(passesFilters(1.5, true, true, null))
        assertFalse(passesFilters(1.5, false, true, null))
    }

    @Test
    fun `passesFilters filters by advanced left side`() {
        val params = CrossDatasetAdvancedFilterParams(
            searchLeft = true,
            searchRight = false,
            minFCLeft = 1.0,
            maxFCLeft = 3.0,
            minFCRight = 0.0,
            maxFCRight = 0.0
        )
        assertTrue(passesFilters(-1.5, false, false, params))
        assertTrue(passesFilters(-2.5, false, false, params))
        assertFalse(passesFilters(-0.5, false, false, params))
        assertFalse(passesFilters(-4.0, false, false, params))
        assertTrue(passesFilters(1.5, false, false, params))
    }

    @Test
    fun `passesFilters filters by advanced right side`() {
        val params = CrossDatasetAdvancedFilterParams(
            searchLeft = false,
            searchRight = true,
            minFCLeft = 0.0,
            maxFCLeft = 0.0,
            minFCRight = 1.0,
            maxFCRight = 3.0
        )
        assertTrue(passesFilters(1.5, false, false, params))
        assertTrue(passesFilters(2.5, false, false, params))
        assertFalse(passesFilters(0.5, false, false, params))
        assertFalse(passesFilters(4.0, false, false, params))
        assertTrue(passesFilters(-1.5, false, false, params))
    }

    @Test
    fun `passesFilters filters by both sides (OR logic)`() {
        val params = CrossDatasetAdvancedFilterParams(
            searchLeft = true,
            searchRight = true,
            minFCLeft = 1.0,
            maxFCLeft = 2.0,
            minFCRight = 1.0,
            maxFCRight = 2.0
        )
        assertTrue(passesFilters(-1.5, false, false, params))
        assertTrue(passesFilters(1.5, false, false, params))
        assertTrue(passesFilters(0.5, false, false, params))
        assertTrue(passesFilters(-0.5, false, false, params))
    }

    @Test
    fun `passesFilters handles null FC with advanced filtering`() {
        val params = CrossDatasetAdvancedFilterParams(
            searchLeft = true,
            searchRight = true,
            minFCLeft = 1.0,
            maxFCLeft = 2.0,
            minFCRight = 1.0,
            maxFCRight = 2.0
        )
        assertTrue(passesFilters(null, false, false, params))
    }

    @Test
    fun `passesFilters validates values within range for both sides`() {
        val params = CrossDatasetAdvancedFilterParams(
            searchLeft = true,
            searchRight = true,
            minFCLeft = 1.0,
            maxFCLeft = 2.0,
            minFCRight = 1.0,
            maxFCRight = 2.0
        )
        assertTrue(passesFilters(-1.0, false, false, params))
        assertTrue(passesFilters(-2.0, false, false, params))
        assertTrue(passesFilters(1.0, false, false, params))
        assertTrue(passesFilters(2.0, false, false, params))
        assertTrue(passesFilters(-1.5, false, false, params))
        assertTrue(passesFilters(1.5, false, false, params))
    }

    @Test
    fun `passesMatrixFilter allows found cells by default`() {
        val cell = MatrixCell(foldChange = 1.5, pValue = 0.01, isSignificant = true, found = true)
        val options = MatrixFilterOptions()
        assertTrue(passesMatrixFilter(cell, options))
    }

    @Test
    fun `passesMatrixFilter hides not found when option set`() {
        val cell = MatrixCell(foldChange = null, pValue = null, isSignificant = false, found = false)
        val optionsHide = MatrixFilterOptions(hideNotFound = true)
        val optionsShow = MatrixFilterOptions(hideNotFound = false)
        assertFalse(passesMatrixFilter(cell, optionsHide))
        assertTrue(passesMatrixFilter(cell, optionsShow))
    }

    @Test
    fun `passesMatrixFilter filters by significant only`() {
        val sigCell = MatrixCell(foldChange = 1.5, pValue = 0.01, isSignificant = true, found = true)
        val nonSigCell = MatrixCell(foldChange = 0.5, pValue = 0.5, isSignificant = false, found = true)
        val options = MatrixFilterOptions(showSignificantOnly = true)
        assertTrue(passesMatrixFilter(sigCell, options))
        assertFalse(passesMatrixFilter(nonSigCell, options))
    }

    @Test
    fun `passesMatrixFilter filters by minimum fold change`() {
        val highFCCell = MatrixCell(foldChange = 2.0, pValue = 0.01, isSignificant = true, found = true)
        val lowFCCell = MatrixCell(foldChange = 0.5, pValue = 0.01, isSignificant = true, found = true)
        val negFCCell = MatrixCell(foldChange = -2.0, pValue = 0.01, isSignificant = true, found = true)
        val options = MatrixFilterOptions(minFoldChange = 1.5)
        assertTrue(passesMatrixFilter(highFCCell, options))
        assertFalse(passesMatrixFilter(lowFCCell, options))
        assertTrue(passesMatrixFilter(negFCCell, options))
    }

    @Test
    fun `passesMatrixFilter filters by maximum p-value`() {
        val lowPCell = MatrixCell(foldChange = 1.5, pValue = 0.01, isSignificant = true, found = true)
        val highPCell = MatrixCell(foldChange = 1.5, pValue = 0.1, isSignificant = false, found = true)
        val options = MatrixFilterOptions(maxPValue = 0.05)
        assertTrue(passesMatrixFilter(lowPCell, options))
        assertFalse(passesMatrixFilter(highPCell, options))
    }

    @Test
    fun `escapeCSV handles plain text`() {
        assertEquals("MAPK1", escapeCSV("MAPK1"))
    }

    @Test
    fun `escapeCSV escapes commas`() {
        assertEquals("\"MAPK1,MAPK3\"", escapeCSV("MAPK1,MAPK3"))
    }

    @Test
    fun `escapeCSV escapes quotes`() {
        assertEquals("\"Gene \"\"MAPK1\"\"\"", escapeCSV("Gene \"MAPK1\""))
    }

    @Test
    fun `escapeCSV escapes newlines`() {
        assertEquals("\"MAPK1\nMAPK3\"", escapeCSV("MAPK1\nMAPK3"))
    }

    @Test
    fun `escapeCSV handles complex string`() {
        assertEquals("\"Gene \"\"MAPK1\"\", found\nin dataset\"", escapeCSV("Gene \"MAPK1\", found\nin dataset"))
    }
}
