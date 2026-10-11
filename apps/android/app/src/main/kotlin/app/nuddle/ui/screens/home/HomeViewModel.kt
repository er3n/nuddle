package app.nuddle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.session.SessionStore
import app.nuddle.ui.components.ComposeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/** Home for now shows the person's name (from register or sync) and keeps what is typed. Tasks arrive in slice 1. */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        sessions: SessionStore,
    ) : ViewModel() {
        private val compose = MutableStateFlow<ComposeState>(ComposeState.Idle)

        val state: StateFlow<HomeUiState?> =
            combine(
                sessions.displayName.filterNotNull(),
                compose,
            ) { name, compose -> HomeUiState(myName = name, compose = compose) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun onComposeTextChange(text: String) {
            compose.update { if (text.isEmpty()) ComposeState.Idle else ComposeState.Typing(text) }
        }
    }
