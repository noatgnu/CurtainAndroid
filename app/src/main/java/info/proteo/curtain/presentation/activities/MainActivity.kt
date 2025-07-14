package info.proteo.curtain

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import info.proteo.curtain.utils.ThemeHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var exampleButton: MaterialButton

    @Inject
    lateinit var curtainRepository: CurtainRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply saved theme preference
        ThemeHelper.applyTheme(this)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_main)
        
        // Handle window insets for proper spacing
        setupWindowInsets()

        // Set up the toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Set up navigation
        drawerLayout = findViewById(R.id.drawerLayout)
        val navView = findViewById<NavigationView>(R.id.navView)

        // Get the NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Configure the drawer layout and toolbar with navigation
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.curtainListFragment),
            drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Add custom navigation listener for non-nav graph destinations
        navView.setNavigationItemSelectedListener(this)

        // Set up the example button
        exampleButton = findViewById(R.id.btn_load_example)
        exampleButton.setOnClickListener {
            loadExampleCurtain()
        }

        // Check for curtain data and update button visibility
        checkCurtainDataAndUpdateUI()

        // Handle the intent if the app was launched from a deep link
        handleIntent(intent)
    }

    private fun setupWindowInsets() {
        val rootView = findViewById<DrawerLayout>(R.id.drawerLayout)
        val appBarLayout = findViewById<View>(R.id.appBarLayout)
        val navHostFragment = findViewById<View>(R.id.nav_host_fragment)
        
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply top inset to app bar
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

    private fun checkCurtainDataAndUpdateUI() {
        lifecycleScope.launch {
            curtainRepository.getAllCurtains().collectLatest { curtains ->
                exampleButton.visibility = if (curtains.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }


    private fun loadExampleCurtain() {
        // Example curtain parameters
        val exampleUniqueId = "f4b009f3-ac3c-470a-a68b-55fcadf68d0f"
        val exampleApiUrl = "https://celsus.muttsu.xyz/"
        val exampleFrontendUrl = "https://curtain.proteo.info/"

        // Load the example curtain
        loadCurtain(exampleUniqueId, exampleApiUrl, exampleFrontendUrl)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle the intent when the app is already running and receives a new deep link
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            when (it.action) {
                Intent.ACTION_VIEW -> {
                    // This is triggered when the app is opened from a deep link
                    val uri = it.data ?: return
                    handleDeepLink(uri)
                }
            }
        }
    }

    private fun handleDeepLink(uri: Uri) {
        try {
            // Extract parameters from deep link
            val scheme = uri.scheme
            val host = uri.host

            // Check if this is the correct scheme and host we're expecting
            if (scheme == "curtain" && host == "open") {
                // Extract uniqueId, apiURL, and frontendURL from the URI
                val uniqueId = uri.getQueryParameter("uniqueId")
                val apiURL = uri.getQueryParameter("apiURL")
                val frontendURL = uri.getQueryParameter("frontendURL") ?: uri.getQueryParameter("frontendURL")

                if (!uniqueId.isNullOrEmpty() && !apiURL.isNullOrEmpty()) {
                    // Load and display the curtain with these parameters
                    loadCurtain(uniqueId, apiURL, frontendURL)
                } else {
                    Toast.makeText(this, "Invalid link: Missing required parameters", Toast.LENGTH_LONG).show()
                }
            } else {
                // Handle other URI schemes or hosts if necessary
                Toast.makeText(this, "Unknown link format", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // Handle any parsing errors
            Toast.makeText(this, "Error processing link: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadCurtain(uniqueId: String, apiURL: String, frontendURL: String? = null) {
        // Show a loading indicator
        val message = if (frontendURL != null) {
            "Loading curtain: $uniqueId from $apiURL (frontend: $frontendURL)"
        } else {
            "Loading curtain: $uniqueId from $apiURL"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        // Use lifecycleScope to launch a coroutine
        lifecycleScope.launch {
            try {
                // Here you would use your repository to load the curtain
                // For example:
                val result = curtainRepository.fetchCurtainByLinkIdAndHost(uniqueId, apiURL, frontendURL)

                // Navigate to appropriate fragment or show content based on the result
                // For now, just showing a toast with the result
                Toast.makeText(this@MainActivity, "Successfully loaded curtain", Toast.LENGTH_SHORT).show()

                // Navigate to the appropriate fragment to display the curtain
                val navController = findNavController(R.id.nav_host_fragment)
                // You might want to create a specific action in your nav graph for this
                navController.navigate(R.id.curtainListFragment)

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Failed to load curtain: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                // Navigate to home fragment using the nav graph
                findNavController(R.id.nav_host_fragment).navigate(R.id.curtainListFragment)
            }
            R.id.nav_filter_lists -> {
                // Navigate to the DataFilterListActivity using the nav graph action
                findNavController(R.id.nav_host_fragment).navigate(R.id.action_curtainListFragment_to_dataFilterListActivity)
            }
            R.id.nav_settings -> {
                // Navigate to Settings Fragment using navigation graph
                findNavController(R.id.nav_host_fragment).navigate(R.id.action_curtainListFragment_to_settingsFragment)
            }
        }

        // Close drawer after handling the item click
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        // Close drawer first if it's open
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
