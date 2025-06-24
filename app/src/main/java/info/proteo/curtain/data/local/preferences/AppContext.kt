package info.proteo.curtain

import android.app.Application
import android.content.Context

/**
 * Utility class to access application context from anywhere in the app
 */
class AppContext {
    companion object {
        private lateinit var appContext: Context

        fun initialize(application: Application) {
            appContext = application.applicationContext
        }

        fun get(): Context {
            return appContext
        }
    }
}
