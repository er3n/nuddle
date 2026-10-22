package app.nuddle.ui.screens.devices

import app.nuddle.ui.screens.home.WrittenText

/** One phone my account is open on (5e). [model] is null until that phone reports one: "Bilinmeyen telefon". */
data class PhoneRowUi(
    val model: String?,
    val appVersion: String?,
    val lastSeen: WrittenText,
    /** "Bu telefon" instead of the time. */
    val current: Boolean,
)

/** What the list shows: a spinner, the alert with "Tekrar dene", or the phones. */
sealed interface PhonesList {
    data object Loading : PhonesList

    data object Failed : PhonesList

    data class Shown(
        val phones: List<PhoneRowUi>,
    ) : PhonesList
}

/** "Diğer cihazlardan çıkılsın mı?": [busy] while it goes out, [failed] when it did not. */
data class SignOutOthersSheet(
    val busy: Boolean = false,
    val failed: Boolean = false,
)

/** 5e Cihazlarım (NUD-142, ADR 0027). */
data class DevicesUiState(
    val list: PhonesList,
    val sheet: SignOutOthersSheet? = null,
) {
    /** "Diğer cihazlardan çıkış yap" only when another phone is listed. */
    val canSignOutOthers: Boolean get() = (list as? PhonesList.Shown)?.phones?.any { !it.current } == true
}

data class DevicesCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSignOutOthers: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)
