package de.psdev.devdrawer.adapters

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import de.psdev.devdrawer.activities.WidgetConfigFragment
import de.psdev.devdrawer.database.WidgetConfig


class WidgetAdapter(fragmentManager: FragmentManager): FragmentStatePagerAdapter(fragmentManager) {

    private val items = arrayListOf<WidgetConfig>()

    fun setItems(widgets: List<WidgetConfig>) {
        items.apply {
            clear()
            addAll(widgets)
        }
        notifyDataSetChanged()
    }

    // ==========================================================================================================================
    // FragmentStatePagerAdapter
    // ==========================================================================================================================

    override fun getItem(position: Int): Fragment = WidgetConfigFragment.newInstance(items[position].widgetId)

    override fun getCount(): Int = items.size

    override fun getPageTitle(position: Int): CharSequence = items[position].name

    // ==========================================================================================================================
    // Public API
    // ==========================================================================================================================

    fun getCurrentWidgetId(currentItem: Int): Int = items[currentItem].widgetId
}