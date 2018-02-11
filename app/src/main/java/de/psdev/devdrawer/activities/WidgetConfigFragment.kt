package de.psdev.devdrawer.activities

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import de.psdev.devdrawer.DevDrawerApplication
import de.psdev.devdrawer.R
import de.psdev.devdrawer.adapters.FilterListAdapter
import de.psdev.devdrawer.database.PackageFilter
import de.psdev.devdrawer.database.PackageFilterDao
import de.psdev.devdrawer.utils.Constants
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_widget_config.*
import mu.KLogging

class WidgetConfigFragment: Fragment() {

    companion object: KLogging() {

        private const val ARG_WIDGET_ID = "ARG_WIDGET_ID"

        fun newInstance(widgetId: Int): WidgetConfigFragment = WidgetConfigFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_WIDGET_ID, widgetId)
            }
        }
    }

    private val devDrawerDatabase by lazy { (activity!!.applicationContext as DevDrawerApplication).devDrawerDatabase }
    private val packageFilterDao: PackageFilterDao by lazy { devDrawerDatabase.packageFilterDao() }
    private val filterListAdapter: FilterListAdapter by lazy { FilterListAdapter(activity!!, devDrawerDatabase, widgetId) }
    private val subscriptions = CompositeDisposable()
    private val widgetId by lazy { arguments?.getInt(ARG_WIDGET_ID) ?: -1 }

    // ==========================================================================================================================
    // Fragment Lifecycle
    // ==========================================================================================================================

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            inflater.inflate(R.layout.fragment_widget_config, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        subscriptions += packageFilterDao.filtersForWidget(widgetId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    filterListAdapter.data = it
                }

        packagesFilterListView.adapter = filterListAdapter
    }

    override fun onDestroyView() {
        subscriptions.clear()
        super.onDestroyView()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            Constants.EDIT_DIALOG_CHANGE -> {
                if (widgetId == data?.getIntExtra("widgetId", -1)) {
                    val id = data.getIntExtra("id", -1)
                    val newFilter = data.getStringExtra("newText")
                    packageFilterDao.updateFilter(id, newFilter, widgetId)
                }
            }
        }
    }

    // ==========================================================================================================================
    // Public API
    // ==========================================================================================================================

    fun addFilter(filter: String, widgetId: Int) {
        if (filter.isNotEmpty() && widgetId == this.widgetId) {
            if (!filterListAdapter.data.map { it.filter }.contains(filter)) {
                logger.warn { "add filter ($filter) for widget:$widgetId" }
                subscriptions += packageFilterDao.addFilterAsync(PackageFilter(filter = filter, widgetId = widgetId))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeBy(onComplete = {
                            activity?.addPackageEditText?.setText("")
                            activity?.sendBroadcast(Intent(Constants.ACTION_REFRESH_APPS).apply {
                                setPackage(context!!.packageName)
                            })
                        })
            } else {
                Toast.makeText(context, "Filter already exists", Toast.LENGTH_SHORT).show()
            }
        }
    }
}