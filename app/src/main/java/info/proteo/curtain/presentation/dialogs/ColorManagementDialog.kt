package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import info.proteo.curtain.R
import info.proteo.curtain.data.models.SearchList
import info.proteo.curtain.data.services.ColorManagementService
import info.proteo.curtain.data.services.SearchService
import info.proteo.curtain.databinding.DialogColorManagementBinding
import info.proteo.curtain.databinding.ItemColorAssignmentBinding
import info.proteo.curtain.databinding.ItemColorPaletteBinding
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ColorManagementDialog : DialogFragment() {

    private var _binding: DialogColorManagementBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var colorManagementService: ColorManagementService
    
    @Inject
    lateinit var searchService: SearchService

    private lateinit var palettePreviewAdapter: ColorManagementPaletteAdapter
    private lateinit var colorAssignmentAdapter: ColorAssignmentAdapter
    
    private var currentSearchLists = listOf<SearchList>()
    private var selectedPalette = "pastel"

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogColorManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupPaletteSelector()
        setupPalettePreview()
        setupColorAssignments()
        setupButtons()
        
        loadCurrentData()
    }

    private fun setupPaletteSelector() {
        val palettes = colorManagementService.getAvailableColorPalettes()
        val paletteNames = palettes.keys.toList()
        
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            paletteNames.map { it.replaceFirstChar { char -> char.uppercase() } }
        )
        binding.paletteSpinner.setAdapter(adapter)
        
        binding.paletteSpinner.setOnItemClickListener { _, _, position, _ ->
            selectedPalette = paletteNames[position]
            updatePalettePreview()
        }
        
        // Set initial selection
        selectedPalette = "pastel"
        binding.paletteSpinner.setText(selectedPalette.replaceFirstChar { it.uppercase() }, false)
    }

    private fun setupPalettePreview() {
        palettePreviewAdapter = ColorManagementPaletteAdapter()
        binding.palettePreviewGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), 5)
            adapter = palettePreviewAdapter
        }
    }

    private fun setupColorAssignments() {
        colorAssignmentAdapter = ColorAssignmentAdapter { searchList, newColor ->
            changeSearchListColor(searchList, newColor)
        }
        
        binding.searchListColorsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = colorAssignmentAdapter
        }
    }

    private fun setupButtons() {
        binding.resetColorsButton.setOnClickListener {
            resetAllColors()
        }
        
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
        
        binding.applyButton.setOnClickListener {
            applyChanges()
        }
    }

    private fun loadCurrentData() {
        lifecycleScope.launch {
            searchService.searchSession.collect { session ->
                currentSearchLists = session.searchLists
                colorAssignmentAdapter.submitList(currentSearchLists)
                
                binding.noSearchListsText.visibility = 
                    if (currentSearchLists.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        
        updatePalettePreview()
    }

    private fun updatePalettePreview() {
        val palette = colorManagementService.getAvailableColorPalettes()[selectedPalette] ?: emptyList()
        palettePreviewAdapter.submitList(palette)
    }

    private fun changeSearchListColor(searchList: SearchList, newColor: String) {
        if (colorManagementService.isValidHexColor(newColor)) {
            val success = searchService.changeSearchListColor(searchList.id, newColor)
            if (!success) {
                showSnackbar("Failed to change color")
            }
        } else {
            showSnackbar("Invalid color format")
        }
    }

    private fun resetAllColors() {
        lifecycleScope.launch {
            colorManagementService.setCurrentColorPalette(selectedPalette)
            colorManagementService.resetColorsToDefault()
            showSnackbar("Colors reset to default palette")
        }
    }

    private fun applyChanges() {
        colorManagementService.setCurrentColorPalette(selectedPalette)
        showSnackbar("Color settings applied")
        dismiss()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): ColorManagementDialog {
            return ColorManagementDialog()
        }
    }
}

class ColorManagementPaletteAdapter : RecyclerView.Adapter<ColorManagementPaletteAdapter.ColorViewHolder>() {
    
    private var colors = listOf<String>()
    
    fun submitList(colorList: List<String>) {
        colors = colorList
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val binding = ItemColorPaletteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ColorViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        holder.bind(colors[position])
    }
    
    override fun getItemCount(): Int = colors.size
    
    inner class ColorViewHolder(
        private val binding: ItemColorPaletteBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(color: String) {
            try {
                val colorInt = android.graphics.Color.parseColor(color)
                binding.colorSquare.setBackgroundColor(colorInt)
            } catch (e: Exception) {
                // Fallback to transparent
                binding.colorSquare.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }
}

class ColorAssignmentAdapter(
    private val onColorChange: (SearchList, String) -> Unit
) : RecyclerView.Adapter<ColorAssignmentAdapter.ColorAssignmentViewHolder>() {
    
    private var searchLists = listOf<SearchList>()
    
    fun submitList(lists: List<SearchList>) {
        searchLists = lists
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorAssignmentViewHolder {
        val binding = ItemColorAssignmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ColorAssignmentViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ColorAssignmentViewHolder, position: Int) {
        holder.bind(searchLists[position])
    }
    
    override fun getItemCount(): Int = searchLists.size
    
    inner class ColorAssignmentViewHolder(
        private val binding: ItemColorAssignmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(searchList: SearchList) {
            binding.apply {
                // Set list name and details
                listName.text = searchList.name
                listDetails.text = "${searchList.proteinIds.size} proteins • ${searchList.searchType.name.replace("_", " ")}"
                
                // Set color indicator
                try {
                    val color = android.graphics.Color.parseColor(searchList.color)
                    colorIndicator.setBackgroundColor(color)
                } catch (e: Exception) {
                    colorIndicator.setBackgroundColor(android.graphics.Color.GRAY)
                }
                
                // Set color text
                colorText.text = searchList.color
                
                // Handle color indicator click to open color picker
                colorIndicator.setOnClickListener {
                    showColorPickerForSearchList(searchList)
                }
            }
        }
        
        private fun showColorPickerForSearchList(searchList: SearchList) {
            val colorPickerDialog = ColorPickerDialog.newInstance(
                listName = searchList.name,
                currentColor = searchList.color
            ) { newColor ->
                onColorChange(searchList, newColor)
            }
            
            val fragmentManager = (itemView.context as androidx.fragment.app.FragmentActivity).supportFragmentManager
            colorPickerDialog.show(fragmentManager, "ColorPickerDialog")
        }
    }
}