package app.nuddle.ui.screens.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.InvitePreviews
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 0a, or 0b once the invite link that opened the app has a preview (ADR 0016). */
@HiltViewModel
class WelcomeViewModel
    @Inject
    constructor(
        private val previews: InvitePreviews,
    ) : ViewModel() {
        val state: StateFlow<WelcomeUiState> =
            previews.current
                .map { WelcomeUiState(inviterName = it?.inviterName) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, WelcomeUiState(previews.current.value?.inviterName))

        fun onInviteLink(code: String) {
            viewModelScope.launch { previews.load(code) }
        }
    }
