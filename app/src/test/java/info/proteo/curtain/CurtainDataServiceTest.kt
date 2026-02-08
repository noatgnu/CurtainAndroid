package info.proteo.curtain

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import info.proteo.curtain.domain.service.CurtainDataService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

class CurtainDataServiceTest {

    private lateinit var service: CurtainDataService
    private lateinit var parseDifferentialForm: Method
    private lateinit var parseSettings: Method

    @Before
    fun setUp() {
        service = CurtainDataService(Gson())

        parseDifferentialForm = CurtainDataService::class.java.getDeclaredMethod(
            "parseDifferentialForm", JsonObject::class.java
        )
        parseDifferentialForm.isAccessible = true

        parseSettings = CurtainDataService::class.java.getDeclaredMethod(
            "parseSettings", JsonObject::class.java
        )
        parseSettings.isAccessible = true
    }

    private fun loadFixture(name: String): JsonObject {
        val json = javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader().readText()
        return JsonParser.parseString(json).asJsonObject
    }

    @Test
    fun `PTM fixture has isPTM true`() {
        val jsonObject = loadFixture("ptm_example_fixture.json")
        val diffForm = parseDifferentialForm.invoke(service, jsonObject)
                as info.proteo.curtain.domain.model.CurtainDifferentialForm

        assertEquals("ProteinID", diffForm.accession)
        assertEquals("Position", diffForm.position)
        assertEquals("Peptide", diffForm.peptideSequence)
        assertEquals("MaxPepProb", diffForm.score)
        assertEquals("SequenceWindow", diffForm.sequence)
        assertEquals("Position.in.peptide", diffForm.positionPeptide)
        assertTrue("isPTM should be true for PTM fixture", diffForm.isPTM)
    }

    @Test
    fun `TP fixture has isPTM false`() {
        val jsonObject = loadFixture("tp_example_fixture.json")
        val diffForm = parseDifferentialForm.invoke(service, jsonObject)
                as info.proteo.curtain.domain.model.CurtainDifferentialForm

        assertEquals("", diffForm.accession)
        assertEquals("", diffForm.position)
        assertFalse("isPTM should be false for TP fixture", diffForm.isPTM)
    }

    @Test
    fun `PTM fixture currentID parsed from settings`() {
        val jsonObject = loadFixture("ptm_example_fixture.json")
        val settings = parseSettings.invoke(service, jsonObject)
                as info.proteo.curtain.domain.model.CurtainSettings

        assertEquals("dd9ad30f-5c46-472c-8c1a-5936babb7652", settings.currentId)
    }

    @Test
    fun `PTM fixture curtainType is PTM`() {
        val jsonObject = loadFixture("ptm_example_fixture.json")
        val diffForm = parseDifferentialForm.invoke(service, jsonObject)
                as info.proteo.curtain.domain.model.CurtainDifferentialForm
        val settings = parseSettings.invoke(service, jsonObject)
                as info.proteo.curtain.domain.model.CurtainSettings

        val curtainData = info.proteo.curtain.domain.model.CurtainData(
            differentialForm = diffForm,
            _settings = settings
        )

        assertEquals("PTM", curtainData.curtainType)
        assertNotNull(curtainData.linkId)
    }

    @Test
    fun `TP fixture curtainType is TP`() {
        val jsonObject = loadFixture("tp_example_fixture.json")
        val diffForm = parseDifferentialForm.invoke(service, jsonObject)
                as info.proteo.curtain.domain.model.CurtainDifferentialForm

        val rawFormElement = jsonObject.get("rawForm")?.asJsonObject
        val samples = mutableListOf<String>()
        rawFormElement?.get("_samples")?.asJsonArray?.forEach { samples.add(it.asString) }

        val rawForm = info.proteo.curtain.domain.model.CurtainRawForm(
            primaryIDs = rawFormElement?.get("_primaryIDs")?.asString ?: "",
            samples = samples
        )

        val curtainData = info.proteo.curtain.domain.model.CurtainData(
            rawForm = rawForm,
            differentialForm = diffForm
        )

        assertEquals("TP", curtainData.curtainType)
        assertFalse(curtainData.differentialForm.isPTM)
    }
}
