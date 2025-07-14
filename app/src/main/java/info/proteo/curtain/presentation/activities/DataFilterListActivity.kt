package info.proteo.curtain.presentation.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import info.proteo.curtain.R
import info.proteo.curtain.utils.ThemeHelper
import info.proteo.curtain.utils.EdgeToEdgeHelper

@AndroidEntryPoint
class DataFilterListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply saved theme preference
        ThemeHelper.applyTheme(this)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_data_filter_list)
        
        // Setup edge-to-edge display
        val appBarLayout = findViewById<View>(R.id.appBarLayout)
        val navHostFragment = findViewById<View>(R.id.nav_host_filter_list)
        EdgeToEdgeHelper.setupActivity(this, appBarLayout, navHostFragment)
        
        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Data Filter Lists"

        // Handle toolbar back button
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // The nav host fragment will handle fragment transactions through the navigation graph
        // No need to manually add DataFilterListFragment as it's the start destination in the nav graph
    }
    

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
