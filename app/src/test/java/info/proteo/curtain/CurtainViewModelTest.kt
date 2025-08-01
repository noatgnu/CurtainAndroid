package info.proteo.curtain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import info.proteo.curtain.data.local.database.CurtainDao
import info.proteo.curtain.data.local.database.entities.CurtainEntity
import info.proteo.curtain.data.repositories.CurtainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class CurtainViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Mock
    private lateinit var curtainDao: CurtainDao

    @Mock
    private lateinit var curtainRepository: CurtainRepository

    private lateinit var viewModel: CurtainViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasCorrectValues() {
        val mockCurtains = listOf(
            CurtainEntity("1", "Test1", "TP", null, true, "host1", 0L, 0L),
            CurtainEntity("2", "Test2", "TP", null, true, "host2", 0L, 0L)
        )
        `when`(curtainDao.getAll()).thenReturn(flowOf(mockCurtains))

        viewModel = CurtainViewModel(curtainDao, curtainRepository)

        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
        assertEquals(0, viewModel.downloadProgress.value)
        assertEquals(0.0, viewModel.downloadSpeed.value, 0.0)
        assertFalse(viewModel.isDownloading.value)
    }

    @Test
    fun loadCurtains_updatesStateCorrectly() = runTest {
        val mockCurtains = listOf(
            CurtainEntity("1", "Test1", "TP", null, true, "host1", 0L, 0L),
            CurtainEntity("2", "Test2", "TP", null, true, "host2", 0L, 0L)
        )
        `when`(curtainDao.getAll()).thenReturn(flowOf(mockCurtains))

        viewModel = CurtainViewModel(curtainDao, curtainRepository)
        
        // Wait for coroutines to complete
        advanceUntilIdle()

        assertEquals(2, viewModel.totalCurtains.value)
        assertEquals(2, viewModel.curtains.value.size)
        verify(curtainDao).getAll()
    }

    @Test
    fun cancelDownload_callsRepositoryCancel() = runTest {
        `when`(curtainDao.getAll()).thenReturn(flowOf(emptyList()))
        viewModel = CurtainViewModel(curtainDao, curtainRepository)

        viewModel.cancelDownload()

        verify(curtainRepository).cancelDownload()
        assertFalse(viewModel.isDownloading.value)
        assertEquals(0, viewModel.downloadProgress.value)
        assertEquals(0.0, viewModel.downloadSpeed.value, 0.0)
    }

    @Test
    fun deleteCurtain_callsRepositoryDelete() = runTest {
        val testCurtain = CurtainEntity("1", "Test", "TP", null, true, "host1", 0L, 0L)
        `when`(curtainDao.getAll()).thenReturn(flowOf(listOf(testCurtain)))
        viewModel = CurtainViewModel(curtainDao, curtainRepository)

        viewModel.deleteCurtain(testCurtain)
        advanceUntilIdle()

        verify(curtainRepository).deleteCurtain("host1", "1")
    }

    @Test
    fun updateCurtainDescription_callsRepositoryUpdate() = runTest {
        val testCurtain = CurtainEntity("1", "Old Description", "TP", null, true, "host1", 0L, 0L)
        `when`(curtainDao.getAll()).thenReturn(flowOf(listOf(testCurtain)))
        viewModel = CurtainViewModel(curtainDao, curtainRepository)

        viewModel.updateCurtainDescription(testCurtain, "New Description")
        advanceUntilIdle()

        verify(curtainRepository).updateCurtainDescription("1", "New Description")
    }

    @Test
    fun togglePinStatus_callsRepositoryUpdate() = runTest {
        val testCurtain = CurtainEntity("1", "Test", "TP", null, true, "host1", 0L, 0L, isPinned = false)
        `when`(curtainDao.getAll()).thenReturn(flowOf(listOf(testCurtain)))
        viewModel = CurtainViewModel(curtainDao, curtainRepository)

        viewModel.togglePinStatus(testCurtain)
        advanceUntilIdle()

        verify(curtainRepository).updatePinStatus("1", true)
    }

    @Test
    fun hasMoreCurtains_returnsTrueInitially() = runTest {
        val largeCurtainList = (1..20).map { 
            CurtainEntity("$it", "Test$it", "TP", null, true, "host1", 0L, 0L)
        }
        `when`(curtainDao.getAll()).thenReturn(flowOf(largeCurtainList))
        viewModel = CurtainViewModel(curtainDao, curtainRepository)
        advanceUntilIdle()

        assertTrue(viewModel.hasMoreCurtains())
    }

    @Test
    fun getPaginationInfo_returnsCorrectFormat() = runTest {
        val mockCurtains = (1..3).map { 
            CurtainEntity("$it", "Test$it", "TP", null, true, "host1", 0L, 0L)
        }
        `when`(curtainDao.getAll()).thenReturn(flowOf(mockCurtains))
        viewModel = CurtainViewModel(curtainDao, curtainRepository)
        advanceUntilIdle()

        val paginationInfo = viewModel.getPaginationInfo()
        assertTrue(paginationInfo.contains("Showing"))
        assertTrue(paginationInfo.contains("of"))
        assertTrue(paginationInfo.contains("curtains"))
    }
}