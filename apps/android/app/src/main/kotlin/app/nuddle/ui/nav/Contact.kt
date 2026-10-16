package app.nuddle.ui.nav

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri

// Call and Message on cards (docs/product.md, Human channel; docs/ux/platform.md, Intents). The app never calls by
// itself: the dialer opens with the number and the person decides.

internal fun Activity.dial(phone: String) {
    startIfPossible(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
}

/** The system chooser over every app that takes `smsto:`; WhatsApp is not assumed. */
internal fun Activity.message(phone: String) {
    startIfPossible(Intent.createChooser(Intent(Intent.ACTION_SENDTO, "smsto:$phone".toUri()), null))
}

/** A page on nuddle.app in the browser (NUD-87). */
internal fun Activity.openPage(url: String) {
    startIfPossible(Intent(Intent.ACTION_VIEW, url.toUri()))
}

private fun Activity.startIfPossible(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A tablet with no dialer or a phone with no browser: nothing to open, the screen stays as it is.
    }
}
