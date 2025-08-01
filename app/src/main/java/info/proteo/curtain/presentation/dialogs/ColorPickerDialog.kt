package info.proteo.curtain.presentation.dialogs

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import info.proteo.curtain.R
import info.proteo.curtain.databinding.DialogColorPickerBinding
import info.proteo.curtain.databinding.ItemColorPaletteBinding

class ColorPickerDialog : DialogFragment() {
    
    private var _binding: DialogColorPickerBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var colorPaletteAdapter: ColorPaletteAdapter
    private var currentColor: String = "#fd7f6f"
    private var selectedColor: String = "#fd7f6f"
    private var listName: String = ""
    private var onColorSelected: ((String) -> Unit)? = null
    
    // Default color palette matching frontend
    private val defaultColors = listOf(
        "#fd7f6f", "#7eb0d5", "#b2e061", "#bd7ebe", "#ffb55a",
        "#ffee65", "#beb9db", "#fdcce5", "#8bd3c7", "#ff9999",
        "#ea5545", "#f46a9b", "#ef9b20", "#edbf33", "#ede15b",
        "#bdcf32", "#87bc45", "#27aeef", "#b33dc6", "#1f77b4",
        "#ff7f0e", "#2ca02c", "#d62728", "#9467bd", "#8c564b",
        "#e377c2", "#7f7f7f", "#bcbd22", "#17becf"
    )
    
    companion object {
        fun newInstance(
            listName: String,
            currentColor: String,
            onColorSelected: (String) -> Unit
        ): ColorPickerDialog {
            return ColorPickerDialog().apply {
                this.listName = listName
                this.currentColor = currentColor
                this.selectedColor = currentColor
                this.onColorSelected = onColorSelected
            }
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogColorPickerBinding.inflate(layoutInflater)
        
        setupViews()
        setupColorPalette()
        setupCustomColorInput()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton("Apply") { _, _ ->
                if (isValidHexColor(currentColor)) {
                    onColorSelected?.invoke(currentColor)
                } else {
                    binding.customColorInputLayout.error = "Please select a valid color"
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset") { _, _ ->
                // Reset to first default color
                val defaultColor = defaultColors.first()
                currentColor = defaultColor
                updateColorPreview(defaultColor)
                binding.customColorInput.setText(defaultColor)
                colorPaletteAdapter.setSelectedColor(defaultColor)
            }
            .create().apply {
                setOnShowListener {
                    // Set icons for buttons after dialog is shown
                    getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.apply {
                        text = ""
                        setCompoundDrawablesWithIntrinsicBounds(
                            androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_check), 
                            null, null, null
                        )
                        contentDescription = "Apply"
                    }
                    getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.apply {
                        text = ""
                        setCompoundDrawablesWithIntrinsicBounds(
                            androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_close), 
                            null, null, null
                        )
                        contentDescription = "Cancel"
                    }
                    getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.apply {
                        text = ""
                        setCompoundDrawablesWithIntrinsicBounds(
                            androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_refresh), 
                            null, null, null
                        )
                        contentDescription = "Reset"
                    }
                }
            }
    }
    
    private fun setupViews() {
        binding.listNameText.text = "List: $listName"
        updateColorPreview(currentColor)
    }
    
    private fun setupColorPalette() {
        colorPaletteAdapter = ColorPaletteAdapter(defaultColors) { color ->
            currentColor = color
            updateColorPreview(color)
            binding.customColorInput.setText(color)
        }
        
        binding.colorPaletteGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), 8).apply {
                // Ensure no spacing between grid items
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int = 1
                }
            }
            adapter = colorPaletteAdapter
            // Remove all spacing and padding
            setPadding(0, 0, 0, 0)
            clipToPadding = false
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    outRect.set(0, 0, 0, 0)
                }
            })
        }
        
        // Set initial selection
        colorPaletteAdapter.setSelectedColor(currentColor)
    }
    
    private fun setupCustomColorInput() {
        binding.customColorInput.setText(currentColor)
        
        binding.customColorInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val colorText = s?.toString()?.trim() ?: ""
                if (isValidHexColor(colorText)) {
                    currentColor = colorText
                    updateColorPreview(colorText)
                    colorPaletteAdapter.setSelectedColor(colorText)
                    binding.customColorInputLayout.error = null
                } else if (colorText.isNotEmpty()) {
                    binding.customColorInputLayout.error = "Invalid hex color"
                }
            }
        })
    }
    
    
    private fun updateColorPreview(color: String) {
        if (isValidHexColor(color)) {
            try {
                val colorInt = Color.parseColor(color)
                binding.currentColorPreview.setBackgroundColor(colorInt)
                binding.currentColorText.text = color.uppercase()
            } catch (e: Exception) {
                // Handle invalid color gracefully
                binding.currentColorPreview.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.primary))
                binding.currentColorText.text = "Invalid"
            }
        }
    }
    
    private fun isValidHexColor(color: String): Boolean {
        return try {
            if (!color.startsWith("#") || color.length != 7) {
                false
            } else {
                Color.parseColor(color)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ColorPaletteAdapter(
    private val colors: List<String>,
    private val onColorClick: (String) -> Unit
) : RecyclerView.Adapter<ColorPaletteAdapter.ColorViewHolder>() {
    
    private var selectedColor: String? = null
    
    fun setSelectedColor(color: String) {
        val oldSelected = selectedColor
        selectedColor = color
        
        // Notify changes for old and new selection
        oldSelected?.let { old ->
            val oldIndex = colors.indexOf(old)
            if (oldIndex != -1) notifyItemChanged(oldIndex)
        }
        
        val newIndex = colors.indexOf(color)
        if (newIndex != -1) notifyItemChanged(newIndex)
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
                val colorInt = Color.parseColor(color)
                binding.colorSquare.setBackgroundColor(colorInt)
                
                // Show selection indicator
                binding.selectionIndicator.visibility = if (color == selectedColor) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                
                binding.colorSquare.setOnClickListener {
                    onColorClick(color)
                }
                
            } catch (e: Exception) {
                // Handle invalid color
                binding.colorSquare.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.primary))
                binding.selectionIndicator.visibility = View.GONE
            }
        }
    }
}