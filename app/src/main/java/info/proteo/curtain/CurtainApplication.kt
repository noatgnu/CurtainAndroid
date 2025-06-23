package info.proteo.curtain

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CurtainApplication : Application() {
    // Provide application-wide access to CurtainDataService
    val curtainDataService = CurtainDataService()

    override fun onCreate() {
        super.onCreate()
        // Initialize AppContext with the application instance
        AppContext.initialize(this)
    }
}
