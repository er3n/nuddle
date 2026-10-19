package app.nuddle.ui.screens.announcement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.BuildConfig
import app.nuddle.data.announcement.NewsAnnouncer
import app.nuddle.data.db.AnnouncementDao
import app.nuddle.domain.Ago
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

private const val STOP_TIMEOUT_MS = 5_000L

/** `announcement/{id}` (ADR 0025): the message from the phone's store; opening it marks it read and clears its notification. */
@HiltViewModel
class AnnouncementViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        announcements: AnnouncementDao,
        announcer: NewsAnnouncer,
        clock: Clock,
    ) : ViewModel() {
        private val id: String = checkNotNull(savedState[ARG_ANNOUNCEMENT])

        val state: StateFlow<AnnouncementUiState> =
            announcements
                .observe(id)
                .map { a ->
                    if (a == null) {
                        AnnouncementUiState.Missing
                    } else {
                        AnnouncementUiState.Ready(
                            title = a.title,
                            body = a.body,
                            ago = Ago.between(Instant.fromEpochMilliseconds(a.sentAt), clock.now()),
                            button =
                                if (a.buttonLabel != null && a.buttonTarget != null) {
                                    AnnouncementButtonUi(a.buttonLabel, a.buttonTarget)
                                } else {
                                    null
                                },
                            imageBase = BuildConfig.API_BASE_URL,
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AnnouncementUiState.Loading)

        init {
            viewModelScope.launch {
                announcements.markOpened(id, clock.now().toEpochMilliseconds())
                announcer.withdraw(id)
            }
        }

        companion object {
            const val ARG_ANNOUNCEMENT = "announcementId"
        }
    }
