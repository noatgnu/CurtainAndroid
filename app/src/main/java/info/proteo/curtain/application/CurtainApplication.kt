package info.proteo.curtain.application

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import info.proteo.curtain.AppContext
import info.proteo.curtain.CurtainDataService

@HiltAndroidApp
class CurtainApplication : Application() {
    // Provide application-wide access to CurtainDataService
    val curtainDataService = CurtainDataService()

    override fun onCreate() {
        super.onCreate()
        // Initialize AppContext with the application instance
        AppContext.Companion.initialize(this)
    }
}
