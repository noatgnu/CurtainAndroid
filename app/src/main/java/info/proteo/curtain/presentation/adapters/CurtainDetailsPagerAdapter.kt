package info.proteo.curtain.presentation.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import info.proteo.curtain.presentation.fragments.curtain.CurtainDetailsTabFragment
import info.proteo.curtain.presentation.fragments.curtain.VolcanoPlotTabFragment
import info.proteo.curtain.presentation.fragments.curtain.ProteinDetailListTabFragment

class CurtainDetailsPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CurtainDetailsTabFragment()
            1 -> VolcanoPlotTabFragment()
            2 -> ProteinDetailListTabFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}