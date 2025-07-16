package info.proteo.curtain.presentation.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import info.proteo.curtain.R
import info.proteo.curtain.utils.ThemeHelper

@AndroidEntryPoint
class DataFilterListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply saved theme preference
        ThemeHelper.applyTheme(this)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_data_filter_list)
        
        // Handle window insets for proper spacing
        setupWindowInsets()
        
        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Data Filter Lists"
        
        // Configure toolbar for edge-to-edge with white elements
        setupToolbarStyle(toolbar)

        // Handle toolbar back button
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // The nav host fragment will handle fragment transactions through the navigation graph
        // No need to manually add DataFilterListFragment as it's the start destination in the nav graph
    }
    

    private fun setupWindowInsets() {
        val appBarLayout = findViewById<AppBarLayout>(R.id.appBarLayout)
        val navHostFragment = findViewById<View>(R.id.nav_host_filter_list)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_container)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply top inset to app bar to extend under status bar
            appBarLayout.setPadding(
                appBarLayout.paddingLeft,
                systemBars.top,
                appBarLayout.paddingRight,
                appBarLayout.paddingBottom
            )
            
            // Apply bottom inset to nav host fragment
            navHostFragment.setPadding(
                navHostFragment.paddingLeft,
                navHostFragment.paddingTop,
                navHostFragment.paddingRight,
                systemBars.bottom
            )
            
            insets
        }
    }
    
    private fun setupToolbarStyle(toolbar: Toolbar) {
        // Set toolbar elements to white for better visibility over transparent status bar
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        
        // Set title and subtitle color to white
        toolbar.setTitleTextColor(whiteColor)
        toolbar.setSubtitleTextColor(whiteColor)
        
        // Set navigation icon (back button) to white
        toolbar.navigationIcon?.setTint(whiteColor)
        
        // Set overflow menu icon to white (if present)
        toolbar.overflowIcon?.setTint(whiteColor)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
