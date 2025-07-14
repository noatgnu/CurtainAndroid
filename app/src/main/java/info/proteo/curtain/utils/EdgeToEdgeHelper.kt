package info.proteo.curtain.utils

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

/**
 * Helper class for implementing edge-to-edge display in activities and fragments
 */
object EdgeToEdgeHelper {
    
    /**
     * Sets up edge-to-edge display for an activity
     * 
     * @param activity The activity to configure
     * @param appBarLayout The app bar layout that should extend into the status bar
     * @param contentView The main content view that should respect bottom insets
     */
    fun setupActivity(
        activity: Activity,
        appBarLayout: View? = null,
        contentView: View? = null
    ) {
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        
        // Find root view
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        
        setupWindowInsets(rootView, appBarLayout, contentView)
    }
    
    /**
     * Sets up edge-to-edge display for a fragment
     * 
     * @param fragment The fragment to configure
     * @param rootView The root view of the fragment
     * @param appBarLayout Optional app bar layout (usually handled by parent activity)
     * @param contentView The main content view that should respect insets
     * @param addHorizontalPadding Whether to add horizontal padding for edge-to-edge content
     */
    fun setupFragment(
        fragment: Fragment,
        rootView: View,
        appBarLayout: View? = null,
        contentView: View? = null,
        addHorizontalPadding: Boolean = false
    ) {
        setupWindowInsets(rootView, appBarLayout, contentView, addHorizontalPadding)
    }
    
    /**
     * Sets up window insets handling for proper edge-to-edge display
     */
    private fun setupWindowInsets(
        rootView: View,
        appBarLayout: View? = null,
        contentView: View? = null,
        addHorizontalPadding: Boolean = false
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply top inset to app bar (extends toolbar background into status bar)
            appBarLayout?.setPadding(
                appBarLayout.paddingLeft,
                systemBars.top,
                appBarLayout.paddingRight,
                appBarLayout.paddingBottom
            )
            
            // Apply bottom inset to content view
            contentView?.setPadding(
                contentView.paddingLeft,
                contentView.paddingTop,
                contentView.paddingRight,
                systemBars.bottom
            )
            
            // Apply horizontal insets to both if no specific views provided
            if (appBarLayout == null && contentView == null) {
                view.setPadding(
                    if (addHorizontalPadding) systemBars.left else view.paddingLeft,
                    view.paddingTop,
                    if (addHorizontalPadding) systemBars.right else view.paddingRight,
                    view.paddingBottom
                )
            }
            
            // Add horizontal padding for edge-to-edge content if requested
            if (addHorizontalPadding && contentView != null) {
                contentView.setPadding(
                    contentView.paddingLeft + systemBars.left,
                    contentView.paddingTop,
                    contentView.paddingRight + systemBars.right,
                    contentView.paddingBottom
                )
            }
            
            insets
        }
    }
    
    /**
     * Sets up window insets for WebView content to ensure proper positioning
     */
    fun setupWebView(webView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(webView.parent as View) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply horizontal insets to WebView to prevent content from being hidden
            webView.setPadding(
                systemBars.left,
                0, // Top handled by parent
                systemBars.right,
                0  // Bottom handled by parent
            )
            
            insets
        }
    }
    
    /**
     * Sets up window insets for full-screen content like video players or image viewers
     */
    fun setupFullScreenContent(contentView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // For full-screen content, we typically want to draw behind system bars
            // but still respect safe areas for interactive elements
            view.setPadding(0, 0, 0, 0)
            
            insets
        }
    }
}