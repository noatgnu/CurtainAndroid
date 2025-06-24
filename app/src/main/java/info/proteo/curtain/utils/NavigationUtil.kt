package info.proteo.curtain.utils

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import info.proteo.curtain.R

/**
 * Navigation utilities for the Curtain app
 */
object NavigationUtil {
    /**
     * Navigate to the Curtain Details screen
     * @param activity The activity from which to navigate
     */
    fun navigateToCurtainDetails(activity: Activity?) {
        if (activity == null || activity !is FragmentActivity) return

        try {
            // Find NavController and navigate to the CurtainDetailsFragment
            val navController = activity.findNavController(R.id.nav_host_fragment)
            navController.navigate(R.id.action_to_curtain_details_fragment)
        } catch (e: Exception) {
            // Log the error but don't crash
            android.util.Log.e("NavigationUtil", "Failed to navigate: ${e.message}", e)
        }
    }
}
