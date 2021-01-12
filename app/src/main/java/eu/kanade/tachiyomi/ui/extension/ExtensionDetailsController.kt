package eu.kanade.tachiyomi.ui.extension

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.DialogPreference
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogController
import androidx.preference.ListPreference
import androidx.preference.ListPreferenceDialogController
import androidx.preference.MultiSelectListPreference
import androidx.preference.MultiSelectListPreferenceDialogController
import androidx.preference.Preference
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
import com.jakewharton.rxbinding.view.clicks
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.EmptyPreferenceDataStore
import eu.kanade.tachiyomi.data.preference.SharedPreferencesDataStore
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.view.RecyclerWindowInsetsListener
import eu.kanade.tachiyomi.util.view.applyWindowInsetsForController
import eu.kanade.tachiyomi.widget.preference.ListMatPreference
import kotlinx.android.synthetic.main.extension_detail_controller.*

@SuppressLint("RestrictedApi")
class ExtensionDetailsController(bundle: Bundle? = null) :
    NucleusController<ExtensionDetailsPresenter>(bundle),
    PreferenceManager.OnDisplayPreferenceDialogListener,
    DialogPreference.TargetFragment {

    private var lastOpenPreferencePosition: Int? = null

    private var preferenceScreen: PreferenceScreen? = null

    constructor(pkgName: String) : this(
        Bundle().apply {
            putString(PKGNAME_KEY, pkgName)
        }
    )

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        val themedInflater = inflater.cloneInContext(getPreferenceThemeContext())

        return themedInflater.inflate(R.layout.extension_detail_controller, container, false)
    }

    override fun createPresenter(): ExtensionDetailsPresenter {
        return ExtensionDetailsPresenter(args.getString(PKGNAME_KEY)!!)
    }

    override fun getTitle(): String? {
        return resources?.getString(R.string.extension_info)
    }

    @SuppressLint("PrivateResource")
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        view.applyWindowInsetsForController()

        val extension = presenter.extension ?: return
        val context = view.context

        extension_title.text = extension.name
        extension_version.text = context.getString(R.string.version_, extension.versionName)
        extension_lang.text = context.getString(R.string.language_, LocaleHelper.getDisplayName(extension.lang, context))
        extension_pkg.text = extension.pkgName
        extension.getApplicationIcon(context)?.let { extension_icon.setImageDrawable(it) }
        extension_uninstall_button.clicks().subscribeUntilDestroy {
            presenter.uninstallExtension()
        }

        if (extension.isObsolete) {
            extension_obsolete.visibility = View.VISIBLE
        }

        val themedContext by lazy { getPreferenceThemeContext() }
        val manager = PreferenceManager(themedContext)
        manager.preferenceDataStore = EmptyPreferenceDataStore()
        manager.onDisplayPreferenceDialogListener = this
        val screen = manager.createPreferenceScreen(themedContext)
        preferenceScreen = screen

        val multiSource = extension.sources.size > 1

        for (source in extension.sources) {
            if (source is ConfigurableSource) {
                addPreferencesForSource(screen, source, multiSource)
            }
        }

        manager.setPreferences(screen)

        extension_prefs_recycler.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(context)
        extension_prefs_recycler.adapter = PreferenceGroupAdapter(screen)
        extension_prefs_recycler.addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(context, VERTICAL))
        extension_prefs_recycler.setOnApplyWindowInsetsListener(RecyclerWindowInsetsListener)

        if (screen.preferenceCount == 0) {
            extension_prefs_empty_view.show(
                R.drawable.ic_no_settings_24dp,
                R.string.empty_preferences_for_extension
            )
        }
    }

    override fun onDestroyView(view: View) {
        preferenceScreen = null
        super.onDestroyView(view)
    }

    fun onExtensionUninstalled() {
        router.popCurrentController()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        lastOpenPreferencePosition?.let { outState.putInt(LASTOPENPREFERENCE_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        lastOpenPreferencePosition = savedInstanceState.get(LASTOPENPREFERENCE_KEY) as? Int
    }

    private fun addPreferencesForSource(screen: PreferenceScreen, source: Source, multiSource: Boolean) {
        val context = screen.context

        // TODO
        val dataStore = SharedPreferencesDataStore(/*if (source is HttpSource) {
            source.preferences
        } else {*/
            context.getSharedPreferences("source_${source.id}", Context.MODE_PRIVATE)
            /*}*/
        )

        if (source is ConfigurableSource) {
            if (multiSource) {
                screen.preferenceCategory {
                    title = source.toString()
                }
            }

            val newScreen = screen.preferenceManager.createPreferenceScreen(context)
            source.setupPreferenceScreen(newScreen)

            // Reparent the preferences
            while (newScreen.preferenceCount != 0) {
                val pref = newScreen.getPreference(0)
                pref.isIconSpaceReserved = false
                pref.preferenceDataStore = dataStore
                pref.fragment = "source_${source.id}"
                pref.order = Int.MAX_VALUE // reset to default order

                newScreen.removePreference(pref)
                screen.addPreference(pref)
            }
        }
    }

    private fun getPreferenceThemeContext(): Context {
        val tv = TypedValue()
        activity!!.theme.resolveAttribute(R.attr.preferenceTheme, tv, true)
        return ContextThemeWrapper(activity, tv.resourceId)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (!isAttached) return

        val screen = preference.parent!!

        lastOpenPreferencePosition = (0 until screen.preferenceCount).indexOfFirst {
            screen.getPreference(it) === preference
        }

        if (preference is ListPreference) {
            ListMatPreference(activity, preference.context).apply {
                key = preference.key
                sharedPref = preference.fragment
                otherPref = preference
                preferenceDataStore = preference.preferenceDataStore
                entries = preference.entries.mapNotNull { it.toString() }
                entryValues = preference.entryValues.mapNotNull { it.toString() }
            }.dialog().show()
            return
        }

        val f = when (preference) {
            is EditTextPreference ->
                EditTextPreferenceDialogController
                    .newInstance(preference.getKey())
            is ListPreference ->
                ListPreferenceDialogController
                    .newInstance(preference.getKey())
            is MultiSelectListPreference ->
                MultiSelectListPreferenceDialogController
                    .newInstance(preference.getKey())
            else -> throw IllegalArgumentException(
                "Tried to display dialog for unknown " +
                    "preference type. Did you forget to override onDisplayPreferenceDialog()?"
            )
        }
        f.targetController = this
        f.showDialog(router)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Preference> findPreference(key: CharSequence): T? {
        // We track [lastOpenPreferencePosition] when displaying the dialog
        // [key] isn't useful since there may be duplicates
        return preferenceScreen!!.getPreference(lastOpenPreferencePosition!!) as T
    }

    private companion object {
        const val PKGNAME_KEY = "pkg_name"
        const val LASTOPENPREFERENCE_KEY = "last_open_preference"
    }
}
