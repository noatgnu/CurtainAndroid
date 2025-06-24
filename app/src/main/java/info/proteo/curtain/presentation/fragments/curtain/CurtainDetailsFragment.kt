package info.proteo.curtain.presentation.fragments.curtain

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CurtainDetailsFragment : Fragment() {

    private var _binding: FragmentCurtainDetailsBinding? = null
    private val binding get() = _binding!!

    // Get navigation arguments
    private val args: CurtainDetailsFragmentArgs by navArgs()

    // Access to the ViewModel
    private val viewModel: CurtainDetailsViewModel by activityViewModels()
    private lateinit var pagerAdapter: CurtainDetailsPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCurtainDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

    override fun onDestroyView() {
        super.onDestroyView()

        if (!requireActivity().isChangingConfigurations) {
            viewModel.clearMemory()
        }

        _binding = null
    }
}