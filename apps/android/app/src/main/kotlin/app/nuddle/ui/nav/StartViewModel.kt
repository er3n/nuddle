package app.nuddle.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.SessionStarter
import app.nuddle.data.session.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Picks the first screen: welcome when signed out, name when sign-up stopped before 0e, otherwise home. */
@HiltViewModel
class StartViewModel
    @Inject
    constructor(
        sessions: SessionStore,
        starter: SessionStarter,
    ) : ViewModel() {
        private val start = MutableStateFlow<String?>(null)
        val startRoute: StateFlow<String?> = start.asStateFlow()

        init {
            viewModelScope.launch {
                val session = sessions.current()
                start.value =
                    when {
                        session == null -> Routes.WELCOME
                        !session.registered -> Routes.NAME
                        else -> Routes.HOME
                    }
                // The permission summary and token go up on every launch (docs/domain.md, Device execution).
                if (session?.registered == true) starter.start()
            }
        }
    }
