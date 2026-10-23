package app.nuddle.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.SessionStarter
import app.nuddle.data.session.SessionStore
import app.nuddle.data.session.StoredSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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

        /** Each time the session goes while the app is open: whatever screen is up gives way to welcome (NUD-141). */
        val signedOut: Flow<Unit> = sessions.session.ends()

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

/**
 * One value each time a session that was there goes away: Çıkış yap, Hesabımı sil or a refresh the server rejected
 * (NUD-141). Being signed out from the start is not an end.
 */
internal fun Flow<StoredSession?>.ends(): Flow<Unit> =
    map { it != null }
        .distinctUntilChanged()
        .dropWhile { signedIn -> !signedIn }
        .filter { signedIn -> !signedIn }
        .map { }
