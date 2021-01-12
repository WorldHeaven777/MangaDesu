package eu.kanade.tachiyomi.widget.preference

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.system.toast
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackLogoutDialog(bundle: Bundle? = null) : DialogController(bundle) {

    private val service = Injekt.get<TrackManager>().getService(args.getInt("key"))!!

    constructor(service: TrackService) : this(Bundle().apply { putInt("key", service.id) })

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialDialog(activity!!)
            .title(text = activity!!.getString(R.string.logout_from_, service.name))
            .negativeButton(R.string.cancel)
            .positiveButton(R.string.logout) { _ ->
                service.logout()
                (targetController as? Listener)?.trackLogoutDialogClosed(service)
                activity!!.toast(R.string.successfully_logged_out)
            }
    }

    interface Listener {
        fun trackLogoutDialogClosed(service: TrackService)
    }
}
