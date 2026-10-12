package app.nuddle.ui.screens.profile

import app.nuddle.data.AccountRepository
import app.nuddle.data.AuthRepository
import app.nuddle.domain.IdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

/** The open sheet, and for 7h and 7i one key per opened sheet, so a retry after a lost answer is the same command. */
internal data class OpenSheet(
    val sheet: ProfileSheet,
    val key: String,
)

/** The settings sheets on top of profile (rename, 7g, 7h, 7i), kept apart from ProfileViewModel's switches. */
internal class ProfileSheetHolder(
    private val scope: CoroutineScope,
    private val account: AccountRepository,
    private val auth: AuthRepository,
    private val ids: IdGenerator,
) {
    val open = MutableStateFlow<OpenSheet?>(null)
    private val signedOutChannel = Channel<Unit>(Channel.CONFLATED)

    /** After Çıkış yap or Hesabımı sil: this phone holds nothing of the person any more. */
    val signedOut: Flow<Unit> = signedOutChannel.receiveAsFlow()

    fun show(sheet: ProfileSheet) {
        open.value = OpenSheet(sheet, ids.next())
    }

    fun onNameChange(name: String) = edit<ProfileSheet.Rename> { it.copy(name = name, failed = false) }

    fun onQuietEdge(edge: QuietEdge) = edit<ProfileSheet.Quiet> { it.copy(editing = edge) }

    fun onQuietTime(time: LocalTime) =
        edit<ProfileSheet.Quiet> {
            if (it.editing == QuietEdge.START) it.copy(start = time, failed = false) else it.copy(end = time, failed = false)
        }

    private inline fun <reified T : ProfileSheet> edit(crossinline change: (T) -> ProfileSheet) =
        open.update { o -> (o?.sheet as? T)?.let { o.copy(sheet = change(it)) } ?: o }

    /** Swipe, scrim or Vazgeç; not while the sheet's command is on its way. */
    fun onDismiss() = open.update { if (it?.sheet?.busy == true) it else null }

    fun onConfirm() {
        val current = open.value ?: return
        val sheet = current.sheet
        if (sheet.busy) return
        open.value = current.copy(sheet = sheet.withBusy(busy = true, failed = false))
        scope.launch {
            val ok =
                when (sheet) {
                    is ProfileSheet.Rename -> auth.register(sheet.name.trim())
                    is ProfileSheet.Quiet -> account.setQuietHours(sheet.start, sheet.end)
                    is ProfileSheet.LogOut -> account.logOut(current.key)
                    is ProfileSheet.Delete -> account.deleteAccount(current.key)
                }
            open.value = if (ok) null else current.copy(sheet = sheet.withBusy(busy = false, failed = true))
            if (ok && (sheet is ProfileSheet.LogOut || sheet is ProfileSheet.Delete)) signedOutChannel.send(Unit)
        }
    }
}

private fun ProfileSheet.withBusy(
    busy: Boolean,
    failed: Boolean,
): ProfileSheet =
    when (this) {
        is ProfileSheet.Rename -> copy(busy = busy, failed = failed)
        is ProfileSheet.Quiet -> copy(busy = busy, failed = failed)
        is ProfileSheet.LogOut -> copy(busy = busy, failed = failed)
        is ProfileSheet.Delete -> copy(busy = busy, failed = failed)
    }
