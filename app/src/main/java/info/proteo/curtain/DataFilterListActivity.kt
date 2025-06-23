package info.proteo.curtain

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DataFilterListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_filter_list)

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
