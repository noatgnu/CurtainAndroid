package info.proteo.curtain.domain.repository

import info.proteo.curtain.data.local.entity.CurtainEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Curtain dataset operations.
 * Defines the contract for data access combining local and remote sources.
 *
 * Matches iOS CurtainRepository protocol.
 */
interface CurtainRepository {

    /**
     * Get all curtains from local database as Flow.
     * Automatically updates when database changes.
     *
     * @return Flow of all curtain entities
     */
    fun getAllCurtains(): Flow<List<CurtainEntity>>

    /**
     * Get curtains from a specific backend hostname.
     *
     * @param hostname Backend server hostname
     * @return Flow of curtain entities from the host
     */
    fun getCurtainsByHostname(hostname: String): Flow<List<CurtainEntity>>

    /**
     * Get all pinned curtains.
     *
     * @return Flow of pinned curtain entities
     */
    fun getPinnedCurtains(): Flow<List<CurtainEntity>>

    /**
     * Search curtains by description or link ID.
     *
     * @param query Search query
     * @return Flow of matching curtain entities
     */
    fun searchCurtains(query: String): Flow<List<CurtainEntity>>

    /**
     * Get a specific curtain by link ID.
     *
     * @param linkId Unique identifier
     * @return Curtain entity or null if not found
     */
    suspend fun getCurtainById(linkId: String): CurtainEntity?

    /**
     * Download curtain data file (JSON) from backend.
     * Matches iOS downloadCurtainData() method with progress tracking.
     *
     * @param curtain Curtain entity to download
     * @param onProgress Callback for download progress (progress percentage, speed in KB/s)
     * @return Result with local file path on success
     */
    suspend fun downloadCurtainData(
        curtain: CurtainEntity,
        onProgress: (Int, Double) -> Unit = { _, _ -> }
    ): Result<String>

    /**
     * Insert or update a curtain in local database.
     *
     * @param curtain Curtain entity to insert
     */
    suspend fun insertCurtain(curtain: CurtainEntity)

    /**
     * Insert multiple curtains in local database.
     *
     * @param curtains List of curtain entities
     */
    suspend fun insertAll(curtains: List<CurtainEntity>)

    /**
     * Update curtain description.
     *
     * @param linkId Curtain link ID
     * @param description New description
     */
    suspend fun updateCurtainDescription(linkId: String, description: String)

    /**
     * Toggle pin status of a curtain.
     *
     * @param linkId Curtain link ID
     * @param isPinned New pin status
     */
    suspend fun updatePinStatus(linkId: String, isPinned: Boolean)

    /**
     * Update the local file path after download.
     *
     * @param linkId Curtain link ID
     * @param filePath Local file path
     */
    suspend fun updateFilePath(linkId: String, filePath: String)

    /**
     * Delete a curtain from local database.
     * Also deletes the associated data file if it exists.
     *
     * @param curtain Curtain entity to delete
     */
    suspend fun deleteCurtain(curtain: CurtainEntity)

    /**
     * Delete a curtain by link ID.
     *
     * @param linkId Curtain link ID
     */
    suspend fun deleteCurtainById(linkId: String)

    /**
     * Delete all curtains from a specific hostname.
     *
     * @param hostname Backend server hostname
     */
    suspend fun deleteCurtainsByHostname(hostname: String)

    /**
     * Get total count of curtains in database.
     *
     * @return Number of curtains
     */
    suspend fun getCurtainCount(): Int

    /**
     * Fetch a specific curtain by link ID from backend and save to local database.
     * Matches iOS loadCurtain(linkId:apiUrl:frontendUrl:) method.
     *
     * @param linkId Unique curtain identifier
     * @param hostname Backend API URL
     * @param frontendURL Frontend URL (optional)
     * @return Result with curtain entity on success
     */
    suspend fun fetchCurtainByLinkIdAndHost(
        linkId: String,
        hostname: String,
        frontendURL: String? = null
    ): Result<CurtainEntity>
}
