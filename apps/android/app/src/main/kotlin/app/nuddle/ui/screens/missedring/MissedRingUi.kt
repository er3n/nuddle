package app.nuddle.ui.screens.missedring

import androidx.compose.runtime.Immutable
import app.nuddle.ui.screens.home.WrittenText

/** One brand step of 2h. */
@Immutable
data class BrandStepUi(
    val id: String,
    val title: String,
    val hint: String,
    val done: Boolean,
)

/** 2h: when the poke should have rung, why this brand loses pokes, and the steps that fix it. */
@Immutable
data class MissedRingUiState(
    /** "dün 08:15"; null when no loss is known any more. */
    val lostAt: WrittenText? = null,
    /** "Xiaomi telefonlar uygulamaları arka planda durdurabiliyor."; null for phones without brand steps. */
    val reason: String? = null,
    val steps: List<BrandStepUi> = emptyList(),
    val loading: Boolean = true,
    /** The steps could not be loaded. */
    val failed: Boolean = false,
) {
    /** The step to do now: the first one not done. */
    val active: String? get() = steps.firstOrNull { !it.done }?.id
}

@Immutable
data class MissedRingCallbacks(
    val onBack: () -> Unit = {},
    val onOpen: (stepId: String) -> Unit = {},
    val onRetry: () -> Unit = {},
)
