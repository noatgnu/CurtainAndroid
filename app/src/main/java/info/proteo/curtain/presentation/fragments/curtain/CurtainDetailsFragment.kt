package info.proteo.curtain.presentation.fragments.curtain

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import info.proteo.curtain.databinding.FragmentCurtainDetailsBinding
import info.proteo.curtain.presentation.viewmodels.CurtainDetailsViewModel
import info.proteo.curtain.presentation.adapters.CurtainDetailsPagerAdapter
import info.proteo.curtain.presentation.fragments.curtain.CurtainDetailsFragmentArgs
import info.proteo.curtain.presentation.dialogs.CurtainSettingsManagerDialog
import info.proteo.curtain.presentation.dialogs.ConditionColorManagementDialog
import info.proteo.curtain.presentation.dialogs.ProteinSearchDialog
import info.proteo.curtain.data.services.SearchService
import info.proteo.curtain.R
import info.proteo.curtain.utils.EdgeToEdgeHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CurtainDetailsFragment : Fragment() {

    private var _binding: FragmentCurtainDetailsBinding? = null
    private val binding get() = _binding!!

    // Get navigation arguments
    private val args: CurtainDetailsFragmentArgs by navArgs()

    // Access to the ViewModel
    private val viewModel: CurtainDetailsViewModel by activityViewModels()
    private lateinit var pagerAdapter: CurtainDetailsPagerAdapter
    
    @Inject
    lateinit var searchService: SearchService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCurtainDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EdgeToEdgeHelper.setupFragment(this, binding.root, contentView = binding.viewPager)

        // Show loading state immediately
        showLoadingState(true)

        // Allow the navigation animation to complete before starting the loading
        lifecycleScope.launch {
            // Delay loading to allow navigation animation to complete
            delay(300)

            // Now pass the curtain ID from arguments to the ViewModel
            viewModel.loadCurtainData(args.curtainId)
        }

        // Observe data from ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isDataLoaded.collectLatest { isLoaded ->
                        if (isLoaded) {
                            showLoadingState(false)
                            setupTabs()
                        }
                    }
                }

                launch {
                    viewModel.error.collectLatest { errorMessage ->
                        errorMessage?.let {
                            showLoadingState(false)
                            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun setupTabs() {
        // Initialize adapter with Fragment Manager
        pagerAdapter = CurtainDetailsPagerAdapter(childFragmentManager, lifecycle)

        // Setup ViewPager2 with adapter
        binding.viewPager.adapter = pagerAdapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "Details"
                1 -> tab.text = "Volcano Plot"
                2 -> tab.text = "Protein Details"
            }
        }.attach()
    }

    private fun showLoadingState(isLoading: Boolean) {
        binding.apply {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            loadingTextView.visibility = if (isLoading) View.VISIBLE else View.GONE
            contentLayout.visibility = if (isLoading) View.GONE else View.VISIBLE
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.curtain_settings_menu, menu)
        
        // Tint all menu icons white for better visibility against primary color toolbar
        setupMenuIconsWhite(menu)
        
        super.onCreateOptionsMenu(menu, inflater)
    }
    
    private fun setupMenuIconsWhite(menu: Menu) {
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.white)
        
        // Tint all menu item icons white
        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            menuItem.icon?.setTint(whiteColor)
            
            // Handle submenu items if any
            if (menuItem.hasSubMenu()) {
                val subMenu = menuItem.subMenu
                if (subMenu != null) {
                    for (j in 0 until subMenu.size()) {
                        subMenu.getItem(j).icon?.setTint(whiteColor)
                    }
                }
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_manage_settings -> {
                showSettingsManagerDialog()
                true
            }
            R.id.action_condition_colors -> {
                showConditionColorsDialog()
                true
            }
            R.id.action_protein_search -> {
                showProteinSearchDialog()
                true
            }
            R.id.action_share_qr_code -> {
                showQRCodeShare()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showSettingsManagerDialog() {
        val dialog = CurtainSettingsManagerDialog.newInstance(args.curtainId)
        dialog.show(childFragmentManager, "SettingsManagerDialog")
    }
    
    private fun showConditionColorsDialog() {
        // Show condition color management dialog
        val dialog = ConditionColorManagementDialog.newInstance()
        dialog.show(childFragmentManager, "ConditionColorsDialog")
    }
    
    private fun showProteinSearchDialog() {
        // Show protein search dialog
        val dialog = ProteinSearchDialog.newInstance()
        dialog.show(childFragmentManager, "ProteinSearchDialog")
    }
    
    private fun showQRCodeShare() {
        // Get the curtain entity to extract linkId and frontendURL
        viewModel.curtainEntity.value?.let { curtainEntity ->
            val linkId = curtainEntity.linkId
            val frontendUrl = curtainEntity.frontendURL ?: "https://curtain.proteo.info/"
            
            val intent = info.proteo.curtain.presentation.activities.QRCodeShareActivity.createIntent(
                requireContext(),
                linkId,
                frontendUrl
            )
            startActivity(intent)
        } ?: run {
            android.widget.Toast.makeText(
                requireContext(),
                "Curtain data not available",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        if (!requireActivity().isChangingConfigurations) {
            viewModel.clearMemory()
        }

        _binding = null
    }
}