package info.proteo.curtain

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import info.proteo.curtain.domain.model.CustomPTMSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PTMDataParsingTest {

    private lateinit var gson: Gson

    @Before
    fun setUp() {
        gson = Gson()
    }

    private fun loadFixture(name: String): String {
        return javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader().readText()
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
            }
        }

        return result.mapValues { it.value.distinctBy { site -> site.position }.sortedBy { site -> site.position } }
    }

    @Test
    fun `parseCustomPTMData parses single database correctly`() {
        val customPTMData = mapOf(
            "TestDB" to mapOf(
                "P12345" to mapOf(
                    "P12345" to listOf(
                        mapOf("position" to 14, "residue" to "S"),
                        mapOf("position" to 31, "residue" to "T")
                    )
                )
            )
        )

        val result = parseCustomPTMData(customPTMData, "P12345", "P12345")

        assertEquals(1, result.size)
        assertTrue(result.containsKey("TestDB"))
        assertEquals(2, result["TestDB"]!!.size)
        assertEquals(15, result["TestDB"]!![0].position)
        assertEquals("S", result["TestDB"]!![0].residue)
        assertEquals(32, result["TestDB"]!![1].position)
        assertEquals("T", result["TestDB"]!![1].residue)
    }

    @Test
    fun `parseCustomPTMData handles multiple databases`() {
        val customPTMData = mapOf(
            "PhosphoSiteCustom" to mapOf(
                "P12345" to mapOf(
                    "P12345" to listOf(
                        mapOf("position" to 14, "residue" to "S")
                    )
                )
            ),
            "CustomDB2" to mapOf(
                "P12345" to mapOf(
                    "P12345" to listOf(
                        mapOf("position" to 49, "residue" to "S")
                    )
                )
            )
        )

        val result = parseCustomPTMData(customPTMData, "P12345", "P12345")

        assertEquals(2, result.size)
        assertTrue(result.containsKey("PhosphoSiteCustom"))
        assertTrue(result.containsKey("CustomDB2"))
    }

    @Test
    fun `parseCustomPTMData handles isoform accession`() {
        val customPTMData = mapOf(
            "TestDB" to mapOf(
                "P12345" to mapOf(
                    "P12345-2" to listOf(
                        mapOf("position" to 17, "residue" to "Y")
                    )
                )
            )
        )

        val result = parseCustomPTMData(customPTMData, "P12345-2", "P12345")

        assertEquals(1, result.size)
        assertEquals(1, result["TestDB"]!!.size)
        assertEquals(18, result["TestDB"]!![0].position)
    }

    @Test
    fun `parseCustomPTMData deduplicates sites by position`() {
        val customPTMData = mapOf(
            "TestDB" to mapOf(
                "P12345" to mapOf(
                    "P12345" to listOf(
                        mapOf("position" to 14, "residue" to "S"),
                        mapOf("position" to 14, "residue" to "S"),
                        mapOf("position" to 31, "residue" to "T")
                    )
                )
            )
        )

        val result = parseCustomPTMData(customPTMData, "P12345", "P12345")

        assertEquals(2, result["TestDB"]!!.size)
    }

    @Test
    fun `parseCustomPTMData sorts sites by position`() {
        val customPTMData = mapOf(
            "TestDB" to mapOf(
                "P12345" to mapOf(
                    "P12345" to listOf(
                        mapOf("position" to 99, "residue" to "S"),
                        mapOf("position" to 14, "residue" to "S"),
                        mapOf("position" to 50, "residue" to "T")
                    )
                )
            )
        )

        val result = parseCustomPTMData(customPTMData, "P12345", "P12345")

        assertEquals(15, result["TestDB"]!![0].position)
        assertEquals(51, result["TestDB"]!![1].position)
        assertEquals(100, result["TestDB"]!![2].position)
    }

    @Test
    fun `parseCustomPTMData handles empty database map`() {
        val customPTMData = emptyMap<String, Any>()
        val result = parseCustomPTMData(customPTMData, "P12345", "P12345")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseCustomPTMData handles missing accession`() {
        val customPTMData = mapOf(
            "TestDB" to mapOf(
                "Q98765" to mapOf(
                    "Q98765" to listOf(
                        mapOf("position" to 14, "residue" to "S")
                    )
                )
            )
        )

        val result = parseCustomPTMData(customPTMData, "P12345", "P12345")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseCustomPTMData parses fixture data correctly`() {
        val json = loadFixture("ptm_comprehensive_fixture.json")
        val jsonObject = JsonParser.parseString(json).asJsonObject
        val settingsObject = jsonObject.getAsJsonObject("settings")
        val customPTMDataElement = settingsObject.get("customPTMData")

        val type = object : TypeToken<Map<String, Any>>() {}.type
        val customPTMData: Map<String, Any> = gson.fromJson(customPTMDataElement, type)

        val result = parseCustomPTMData(customPTMData, "P12345", "P12345")

        assertTrue(result.containsKey("PhosphoSiteCustom"))
        assertTrue(result.containsKey("CustomDB2"))

        val phosphoSites = result["PhosphoSiteCustom"]!!
        assertTrue(phosphoSites.isNotEmpty())
    }

    @Test
    fun `parseCustomPTMData handles numeric position as Double`() {
        val customPTMData = mapOf(
            "TestDB" to mapOf(
                "P12345" to mapOf(
                    "P12345" to listOf(
                        mapOf("position" to 14.0, "residue" to "S")
                    )
                )
            )
        )

        val result = parseCustomPTMData(customPTMData, "P12345", "P12345")

        assertEquals(1, result["TestDB"]!!.size)
        assertEquals(15, result["TestDB"]!![0].position)
    }

    @Test
    fun `parseCustomPTMData handles string position`() {
        val customPTMData = mapOf(
            "TestDB" to mapOf(
                "P12345" to mapOf(
                    "P12345" to listOf(
                        mapOf("position" to "14", "residue" to "S")
                    )
                )
            )
        )

        val result = parseCustomPTMData(customPTMData, "P12345", "P12345")

        assertEquals(1, result["TestDB"]!!.size)
        assertEquals(15, result["TestDB"]!![0].position)
    }

    @Test
    fun `variant correction map parsing`() {
        val json = loadFixture("ptm_comprehensive_fixture.json")
        val jsonObject = JsonParser.parseString(json).asJsonObject
        val settingsObject = jsonObject.getAsJsonObject("settings")

        val variantCorrectionElement = settingsObject.get("variantCorrection")
        val type = object : TypeToken<Map<String, String>>() {}.type
        val variantCorrection: Map<String, String> = gson.fromJson(variantCorrectionElement, type)

        assertEquals("P12345-2", variantCorrection["P12345"])
    }

    @Test
    fun `custom sequences map parsing`() {
        val json = loadFixture("ptm_comprehensive_fixture.json")
        val jsonObject = JsonParser.parseString(json).asJsonObject
        val settingsObject = jsonObject.getAsJsonObject("settings")

        val customSequencesElement = settingsObject.get("customSequences")
        val type = object : TypeToken<Map<String, String>>() {}.type
        val customSequences: Map<String, String> = gson.fromJson(customSequencesElement, type)

        assertNotNull(customSequences["Q98765"])
        assertTrue(customSequences["Q98765"]!!.contains("CUSTOM"))
    }

    @Test
    fun `differential data parsing from fixture`() {
        val json = loadFixture("ptm_comprehensive_fixture.json")
        val jsonObject = JsonParser.parseString(json).asJsonObject

        val diffDataArray = jsonObject.getAsJsonArray("differentialData")

        assertEquals(6, diffDataArray.size())

        val firstEntry = diffDataArray[0].asJsonObject
        assertEquals("P12345_S15_1", firstEntry.get("Index").asString)
        assertEquals("P12345", firstEntry.get("ProteinID").asString)
        assertEquals("S15", firstEntry.get("Position").asString)
        assertEquals(2.5, firstEntry.get("Welch's T-test Difference AO_UT").asDouble, 0.01)
    }

    @Test
    fun `raw data parsing from fixture`() {
        val json = loadFixture("ptm_comprehensive_fixture.json")
        val jsonObject = JsonParser.parseString(json).asJsonObject

        val rawDataArray = jsonObject.getAsJsonArray("rawData")

        assertEquals(6, rawDataArray.size())

        val firstEntry = rawDataArray[0].asJsonObject
        assertEquals("P12345_S15_1", firstEntry.get("T: Index").asString)
        assertTrue(firstEntry.has("UT.01"))
        assertTrue(firstEntry.has("AO.01"))
    }
}
