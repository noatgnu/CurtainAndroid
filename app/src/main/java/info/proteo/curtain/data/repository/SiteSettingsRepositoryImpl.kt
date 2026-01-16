package info.proteo.curtain.data.repository

import info.proteo.curtain.data.local.dao.SiteSettingsDao
import info.proteo.curtain.data.local.entity.CurtainSiteSettingsEntity
import info.proteo.curtain.domain.repository.SiteSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SiteSettingsRepository.
 * Manages backend site settings configuration.
 *
 * Matches iOS site settings management with predefined hosts:
 * - https://celsus.muttsu.xyz/
 * - https://curtain-backend.omics.quest/
 * - https://curtain.proteo.info/ (frontend, not API)
 */
@Singleton
class SiteSettingsRepositoryImpl @Inject constructor(
    private val siteSettingsDao: SiteSettingsDao
) : SiteSettingsRepository {

    init {
        CoroutineScope(Dispatchers.IO).launch {
            migrateOldApiUrls()
        }
    }

    private suspend fun migrateOldApiUrls() {
        val allSettings = siteSettingsDao.getAllSiteSettingsSync()
        allSettings.forEach { setting ->
            if (setting.hostname.contains("/api/")) {
                val newHostname = setting.hostname.replace("/api/", "/")
                siteSettingsDao.updateHostname(setting.hostname, newHostname)
            }
        }
    }

    override fun getAllSiteSettings(): Flow<List<CurtainSiteSettingsEntity>> {
        return siteSettingsDao.getAllSiteSettings()
    }

    override fun getActiveSiteSettings(): Flow<List<CurtainSiteSettingsEntity>> {
        return siteSettingsDao.getActiveSiteSettings()
    }

    override suspend fun getSiteSettingsByHostname(hostname: String): CurtainSiteSettingsEntity? {
        return siteSettingsDao.getSiteSettingsByHostname(hostname)
    }

    override suspend fun siteExists(hostname: String): Boolean {
        return siteSettingsDao.siteExists(hostname)
    }

    override suspend fun insertSiteSettings(settings: CurtainSiteSettingsEntity) {
        siteSettingsDao.insertSiteSettings(settings)
    }

    /**
     * Insert predefined default backend sites.
     * Matches iOS NetworkService predefined hosts.
     *
     * Default sites:
     * 1. Celsus (celsus.muttsu.xyz) - Active by default
     * 2. Omics Quest (curtain-backend.omics.quest) - Active by default
     * 3. Proteo Info (curtain.proteo.info) - Frontend only, inactive by default
     */
    override suspend fun insertDefaultSites() {
        val currentTime = System.currentTimeMillis()

        val defaultSites = listOf(
            CurtainSiteSettingsEntity(
                hostname = "https://celsus.muttsu.xyz/",
                lastSync = 0L,
                active = true,
                apiKey = null,
                notes = "Celsus backend server",
                siteDescription = "Primary Curtain backend hosted by Muttsu",
                requiresAuthentication = false,
                createdAt = currentTime
            ),
            CurtainSiteSettingsEntity(
                hostname = "https://curtain-backend.omics.quest/",
                lastSync = 0L,
                active = true,
                apiKey = null,
                notes = "Omics Quest backend server",
                siteDescription = "Curtain backend for Omics Quest platform",
                requiresAuthentication = false,
                createdAt = currentTime
            ),
            CurtainSiteSettingsEntity(
                hostname = "https://curtain.proteo.info/",
                lastSync = 0L,
                active = false,
                apiKey = null,
                notes = "Proteo Info frontend (web interface only)",
                siteDescription = "Official Curtain web interface - not an API endpoint",
                requiresAuthentication = false,
                createdAt = currentTime
            )
        )

        siteSettingsDao.insertAll(defaultSites)
    }

    override suspend fun updateActiveStatus(hostname: String, active: Boolean) {
        siteSettingsDao.updateActiveStatus(hostname, active)
    }

    override suspend fun updateLastSync(hostname: String, timestamp: Long) {
        siteSettingsDao.updateLastSync(hostname, timestamp)
    }

    override suspend fun updateApiKey(hostname: String, apiKey: String?) {
        siteSettingsDao.updateApiKey(hostname, apiKey)
    }

    override suspend fun deleteSiteSettings(settings: CurtainSiteSettingsEntity) {
        siteSettingsDao.deleteSiteSettings(settings)
    }

    override suspend fun deleteSiteSettingsByHostname(hostname: String) {
        siteSettingsDao.deleteSiteSettingsByHostname(hostname)
    }

    override suspend fun getActiveSiteCount(): Int {
        return siteSettingsDao.getActiveSiteCount()
    }
}
