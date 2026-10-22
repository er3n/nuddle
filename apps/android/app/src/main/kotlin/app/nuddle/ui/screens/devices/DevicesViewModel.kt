package app.nuddle.ui.screens.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.SessionsRepository
import app.nuddle.data.SignedInPhone
import app.nuddle.domain.IdGenerator
import app.nuddle.ui.screens.home.writtenText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlin.time.Clock

/**
 * 5e Cihazlarım (NUD-142, ADR 0027): loads my phones on open and on "Tekrar dene". "Diğer cihazlardan çıkış yap" asks
 * first; one key per opened sheet, so a retry after a lost answer is the same command. Afterwards the list is loaded
 * again and shows only this phone.
 */
@HiltViewModel
class DevicesViewModel
    @Inject
    constructor(
        private val sessions: SessionsRepository,
        private val ids: IdGenerator,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        private val ui = MutableStateFlow(DevicesUiState(PhonesList.Loading))
        val state: StateFlow<DevicesUiState> = ui.asStateFlow()
        private var key = ""

        init {
            load()
        }

        fun onRetry() = load()

        private fun load() {
            ui.update { it.copy(list = PhonesList.Loading) }
            viewModelScope.launch {
                val phones = sessions.phones()
                ui.update { it.copy(list = phones?.let { p -> PhonesList.Shown(p.map(::rowOf)) } ?: PhonesList.Failed) }
            }
        }

        private fun rowOf(phone: SignedInPhone) =
            PhoneRowUi(phone.model, phone.appVersion, writtenText(phone.lastSeenAt, clock.now(), timeZone), phone.current)

        fun onSignOutOthers() {
            if (!ui.value.canSignOutOthers) return
            key = ids.next()
            ui.update { it.copy(sheet = SignOutOthersSheet()) }
        }

        /** Swipe, scrim or Vazgeç; not while the command is on its way. */
        fun onDismiss() = ui.update { if (it.sheet?.busy == true) it else it.copy(sheet = null) }

        fun onConfirm() {
            val sheet = ui.value.sheet ?: return
            if (sheet.busy) return
            ui.update { it.copy(sheet = SignOutOthersSheet(busy = true)) }
            viewModelScope.launch {
                val ok = sessions.signOutOthers(key)
                ui.update { it.copy(sheet = if (ok) null else SignOutOthersSheet(failed = true)) }
                if (ok) load()
            }
        }
    }
