package info.proteo.curtain

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DataFilterListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_data_filter_list)

        // Configure window for proper theming
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Set status bar color and appearance
        val primaryColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0)
        window.statusBarColor = primaryColor
        
        // Set status bar content color based on primary color luminance
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        val isLightColor = MaterialColors.isColorLight(primaryColor)
        windowInsetsController.isAppearanceLightStatusBars = isLightColor
        
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
