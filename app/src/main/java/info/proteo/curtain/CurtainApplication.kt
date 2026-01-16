package info.proteo.curtain

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Curtain Android app.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 *
 * This triggers Hilt's code generation including a base class for the application
 * that serves as the application-level dependency container.
 *
 * Must be registered in AndroidManifest.xml with android:name=".CurtainApplication"
 */
@HiltAndroidApp
class CurtainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Application initialization
        // Room database and other singletons are lazily initialized by Hilt
    }
}
