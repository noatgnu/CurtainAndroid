package info.proteo.curtain.presentation.fragments.curtain

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import info.proteo.curtain.databinding.FragmentCurtainDetailsTabBinding
import info.proteo.curtain.presentation.viewmodels.CurtainDetailsViewModel
import kotlinx.coroutines.launch

class CurtainDetailsTabFragment : Fragment() {
    private var _binding: FragmentCurtainDetailsTabBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CurtainDetailsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCurtainDetailsTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe data changes instead of immediately trying to update UI
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe curtainData changes
                viewModel.curtainData.collect { curtainData ->
                    updateUI()
                }
            }
        }

        binding.backButton.setOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun updateUI() {
        val curtainData = viewModel.curtainData.value
        Log.d("CurtainDetailsTab", "updateUI called, curtainData: ${curtainData != null}")


        // Display data in the UI
        binding.apply {
            curtainData?.let { data ->
                // Sample information
                val samplesInfo = data.rawForm.samples.joinToString(", ")
                dataInfoTextView.text = "Samples: $samplesInfo"

                // DataFrame statistics
                val rawRowCount = data.raw.df.rowsCount()
                val diffRowCount = data.differential.df.rowsCount()
                dataframeInfoTextView.text = "Raw data rows: $rawRowCount\nDifferential data rows: $diffRowCount"

                // UniProt status
                uniprotStatusTextView.text = if (data.bypassUniProt)
                    "UniProt data loaded"
                else
                    "UniProt data not loaded"

                // Add differential form parameters display
                val diffForm = data.differentialForm
                val diffFormText = StringBuilder().apply {
                    append("Primary IDs: ${diffForm.primaryIDs}\n")
                    append("Gene Names: ${diffForm.geneNames}\n")
                    append("Fold Change: ${diffForm.foldChange}\n")
                    append("Transform FC: ${diffForm.transformFC}\n")
                    append("Significance: ${diffForm.significant}\n")
                    append("Transform Significance: ${diffForm.transformSignificant}\n")
                    append("Comparison: ${diffForm.comparison}\n")
                    append("Comparison Select: ${diffForm.comparisonSelect.joinToString(", ")}\n")
                    append("Reverse Fold Change: ${diffForm.reverseFoldChange}")
                }.toString()

                differentialFormTextView.text = diffFormText
            } ?: run {
                // If no data is available, show a message
                dataInfoTextView.text = "No data available"
                dataframeInfoTextView.text = ""
                uniprotStatusTextView.text = ""
                differentialFormTextView.text = ""
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}