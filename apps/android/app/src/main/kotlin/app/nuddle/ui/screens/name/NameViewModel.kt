package app.nuddle.ui.screens.name

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.AuthRepository
import app.nuddle.data.SessionStarter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAX_NAME_LENGTH = 60

/** Sends the `register` command (ADR 0014), then continues home. */
@HiltViewModel
class NameViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val starter: SessionStarter,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(NameUiState())
        val state: StateFlow<NameUiState> = mutableState.asStateFlow()

        private val done = Channel<Unit>(Channel.BUFFERED)
        val registered: Flow<Unit> = done.receiveAsFlow()

        fun onNameChange(name: String) {
            mutableState.update { it.copy(name = name.take(MAX_NAME_LENGTH), failed = false) }
        }

        fun onContinue() {
            val current = mutableState.value
            if (!current.canContinue) return
            mutableState.update { it.copy(saving = true, failed = false) }
            viewModelScope.launch {
                if (auth.register(current.name.trim())) {
                    starter.start()
                    done.send(Unit)
                } else {
                    mutableState.update { it.copy(saving = false, failed = true) }
                }
            }
        }
    }
