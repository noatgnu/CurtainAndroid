package info.proteo.curtain.presentation.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayoutMediator
import info.proteo.curtain.R
import info.proteo.curtain.databinding.ActivityQrcodeShareBinding
import info.proteo.curtain.presentation.fragments.qrcode.AndroidShareFragment
import info.proteo.curtain.presentation.fragments.qrcode.WebShareFragment
import info.proteo.curtain.utils.ThemeHelper

class QRCodeShareActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityQrcodeShareBinding
    private lateinit var linkId: String
    private lateinit var frontendUrl: String
    
    companion object {
        private const val EXTRA_LINK_ID = "extra_link_id"
        private const val EXTRA_FRONTEND_URL = "extra_frontend_url"
        
        fun createIntent(context: Context, linkId: String, frontendUrl: String): Intent {
            return Intent(context, QRCodeShareActivity::class.java).apply {
                putExtra(EXTRA_LINK_ID, linkId)
                putExtra(EXTRA_FRONTEND_URL, frontendUrl)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply saved theme preference
        ThemeHelper.applyTheme(this)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityQrcodeShareBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Handle window insets for proper spacing
        setupWindowInsets()
        
        // Get data from intent
        linkId = intent.getStringExtra(EXTRA_LINK_ID) ?: ""
        frontendUrl = intent.getStringExtra(EXTRA_FRONTEND_URL) ?: ""
        
        if (linkId.isEmpty()) {
            finish()
            return
        }
        
        setupToolbar()
        setupTabs()
    }
    
    private fun setupWindowInsets() {
        val appBarLayout = binding.appBarLayout
        val viewPager = binding.viewPager
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply top inset to app bar to extend under status bar
            appBarLayout.setPadding(
                appBarLayout.paddingLeft,
                systemBars.top,
                appBarLayout.paddingRight,
                appBarLayout.paddingBottom
            )
            
            // Apply bottom inset to view pager
            viewPager.setPadding(
                viewPager.paddingLeft,
                viewPager.paddingTop,
                viewPager.paddingRight,
                systemBars.bottom
            )
            
            insets
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Share Curtain Data"
        }
        
        // Set main toolbar elements to white (title, subtitle, navigation icon)
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        binding.toolbar.setTitleTextColor(whiteColor)
        binding.toolbar.setSubtitleTextColor(whiteColor)
        binding.toolbar.setNavigationIconTint(whiteColor)
        
        // Overflow menu will follow theme via popupTheme in layout
        // This allows overflow menu to be light/dark based on system theme
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupTabs() {
        val adapter = QRCodePagerAdapter(this)
        binding.viewPager.adapter = adapter
        
        // Set tab colors to white
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        binding.tabLayout.setTabTextColors(whiteColor, whiteColor)
        binding.tabLayout.setSelectedTabIndicatorColor(whiteColor)
        binding.tabLayout.tabRippleColor = ContextCompat.getColorStateList(this, R.color.white)
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Web Share"
                1 -> "Android Share"
                else -> ""
            }
            
            val icon = when (position) {
                0 -> getDrawable(R.drawable.ic_web)
                1 -> getDrawable(R.drawable.ic_download)
                else -> null
            }
            
            // Tint the tab icons white
            icon?.setTint(whiteColor)
            tab.icon = icon
        }.attach()
    }
    
    fun getLinkId(): String = linkId
    fun getFrontendUrl(): String = frontendUrl
    
    private inner class QRCodePagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> WebShareFragment.newInstance(linkId, frontendUrl)
                1 -> AndroidShareFragment.newInstance(linkId, frontendUrl)
                else -> WebShareFragment.newInstance(linkId, frontendUrl)
            }
        }
    }
}