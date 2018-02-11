package de.psdev.devdrawer.activities

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import de.psdev.devdrawer.DevDrawerApplication
import de.psdev.devdrawer.R
import de.psdev.devdrawer.adapters.PartialMatchAdapter
import de.psdev.devdrawer.adapters.WidgetAdapter
import de.psdev.devdrawer.appwidget.DDWidgetProvider
import de.psdev.devdrawer.database.WidgetConfig
import de.psdev.devdrawer.database.WidgetConfigDao
import de.psdev.devdrawer.utils.Constants
import de.psdev.devdrawer.utils.consume
import de.psdev.devdrawer.utils.getExistingPackages
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import mu.KLogging

class MainActivity: AppCompatActivity(), TextWatcher {

    companion object: KLogging() {
        @JvmStatic
        fun createStartIntent(context: Context, appWidgetId: Int): Intent = Intent(context, MainActivity::class.java).apply {
            action = "${Constants.ACTION_EDIT_FILTER}$appWidgetId"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    private val devDrawerDatabase by lazy { (application as DevDrawerApplication).devDrawerDatabase }
    private val appPackages: List<String> by lazy { packageManager.getExistingPackages() }
    private val packageNameCompletionAdapter: PartialMatchAdapter by lazy { PartialMatchAdapter(this, appPackages, devDrawerDatabase) }
    private val widgetConfigDao: WidgetConfigDao by lazy { devDrawerDatabase.widgetConfigDao() }
    private val subscriptions = CompositeDisposable()
    private val widgetAdapter by lazy { WidgetAdapter(supportFragmentManager) }
    private val appWidgetId by lazy { getWidgetId() }

    // ==========================================================================================================================
    // Android Lifecycle
    // ==========================================================================================================================

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)

        actionBar?.apply {
            setDisplayShowTitleEnabled(true)
            title = "DevDrawer"
        }

        subscriptions += saveNewWidget()
                .andThen(widgetConfigDao.widgets())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    widgetAdapter.setItems(it)
                    val index = it.indexOfFirst { it.widgetId == appWidgetId }
                    if (index in 0 until it.size) {
                        pager.setCurrentItem(index, false)
                    }
                }
    }

    private fun saveNewWidget(): Completable = if (intent?.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE) {
        widgetConfigDao.addWidgetAsync(WidgetConfig(name = "unnamed ($appWidgetId)", widgetId = appWidgetId))
    } else {
        Completable.complete()
    }

    override fun onContentChanged() {
        super.onContentChanged()
        pager.adapter = widgetAdapter
        addPackageEditText.setAdapter(packageNameCompletionAdapter)
        addPackageEditText.addTextChangedListener(this)
        addButton.setOnClickListener { _ ->
            val widgetId = widgetAdapter.getCurrentWidgetId(pager.currentItem)
            supportFragmentManager.fragments.map { it as WidgetConfigFragment }.forEach { it.addFilter(addPackageEditText.text.toString(), widgetId) }
        }
    }

    override fun onBackPressed() {
        saveWidget()
        super.onBackPressed()
    }

    private fun saveWidget() {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val widget = DDWidgetProvider.createRemoteViews(this, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, widget)
            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        for (fragment in supportFragmentManager.fragments) {
            fragment.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_main, menu)
        menu.findItem(R.id.action_confirm)?.isVisible = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_confirm -> consume { startActivity(Intent(this, PrefActivity::class.java)) }
            R.id.action_settings -> consume { startActivity(Intent(this, PrefActivity::class.java)) }
            R.id.action_info -> consume { TODO("Implement app info screen") }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        subscriptions.clear()
        super.onDestroy()
    }

    // ==========================================================================================================================
    // TextWatcher
    // ==========================================================================================================================

    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {}

    override fun onTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {}

    override fun afterTextChanged(editable: Editable) {
        packageNameCompletionAdapter.filter.filter(editable.toString())
    }

    // ==========================================================================================================================
    // Private API
    // ==========================================================================================================================

    private fun getWidgetId(): Int = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
}

