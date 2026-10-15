package app.nuddle.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtMotion
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.uppercaseForUi

/** The two halves of home once there is someone to write to (docs/ux/screens.md, Home tabs and task detail). */
enum class HomeTab { Mine, Sent }

/** Open tasks to me: what the Bana label counts. */
internal val HomeUiState.mineCount: Int
    get() = (if (nextUp != null) 1 else 0) + later.size + issues.missed.size

/** Something in Gönderdiklerim waits for my answer: the dot on its label. */
internal val HomeUiState.sentWaiting: Boolean
    get() = issues.sent.isNotEmpty() || sent.any { it.awaitingApproval }

/** Bana with a count, Gönderdiklerim with a dot while something waits for an answer (components.md 4). */
@Composable
internal fun HomeTabs(
    tab: HomeTab,
    count: Int,
    waiting: Boolean,
    onTab: (HomeTab) -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.neutralPill, RoundedCornerShape(dimens.radiusM))
                .padding(dimens.tabPadding)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(dimens.tabPadding),
    ) {
        TabSegment(
            label = stringResource(R.string.home_tab_mine),
            selected = tab == HomeTab.Mine,
            onClick = { onTab(HomeTab.Mine) },
            modifier = Modifier.weight(1f).testTag(TestTags.HOME_TAB_MINE),
        ) { if (count > 0) CountBadge(count, tab == HomeTab.Mine) }
        TabSegment(
            label = stringResource(R.string.home_tab_sent),
            selected = tab == HomeTab.Sent,
            onClick = { onTab(HomeTab.Sent) },
            modifier = Modifier.weight(1f).testTag(TestTags.HOME_TAB_SENT),
            description = if (waiting) stringResource(R.string.home_tab_sent_waiting) else null,
        ) {
            if (waiting) Box(Modifier.size(dimens.unreadDot).background(colors.poke, CircleShape))
        }
    }
}

@Composable
private fun TabSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    extra: @Composable () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusS)
    Row(
        modifier =
            modifier
                .height(dimens.tabHeight)
                .clip(shape)
                .background(if (selected) colors.surface else colors.neutralPill, shape)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .semantics(mergeDescendants = true) { if (description != null) contentDescription = "$label, $description" },
        horizontalArrangement = Arrangement.spacedBy(dimens.space6, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = DurtTheme.type.bodySmallStrong,
            color = if (selected) colors.ink else colors.textSecondary,
            maxLines = 1,
        )
        extra()
    }
}

@Composable
private fun CountBadge(
    count: Int,
    selected: Boolean,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Text(
        text = count.toString(),
        style = DurtTheme.type.micro,
        color = if (selected) colors.surface else colors.ink,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .background(if (selected) colors.ink else colors.borderStrong, RoundedCornerShape(dimens.radiusPill))
                .padding(horizontal = dimens.space6),
    )
}

/** The tab's content slides in from the side it is on; a horizontal drag past `size.tabs.swipe` switches. */
@Composable
internal fun TabContent(
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
    content: @Composable ColumnScope.(HomeTab) -> Unit,
) {
    val dimens = DurtTheme.dimens
    val threshold = with(LocalDensity.current) { dimens.tabSwipe.toPx() }
    val dragged = remember { mutableFloatStateOf(0f) }
    val current by rememberUpdatedState(tab)
    AnimatedContent(
        targetState = tab,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val spec = tween<IntOffset>(DurtMotion.MEDIUM_MS)
            slideInHorizontally(spec) { if (forward) it else -it } togetherWith slideOutHorizontally(spec) { if (forward) -it else it }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .draggable(
                    state = rememberDraggableState { dragged.floatValue += it },
                    orientation = Orientation.Horizontal,
                    onDragStarted = { dragged.floatValue = 0f },
                    onDragStopped = {
                        val distance = dragged.floatValue
                        when {
                            distance < -threshold && current == HomeTab.Mine -> onTab(HomeTab.Sent)
                            distance > threshold && current == HomeTab.Sent -> onTab(HomeTab.Mine)
                        }
                    },
                ),
        label = "homeTab",
    ) { shown ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.space16)) { content(shown) }
    }
}

/** A section label of home: "SIRADAKİ", "SONRA", "CEVABINI BEKLİYOR". */
@Composable
internal fun SectionLabel(
    text: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(text).uppercaseForUi(),
        style = DurtTheme.type.overline,
        color = DurtTheme.colors.textTertiary,
        modifier = modifier.semantics { heading() },
    )
}

/** Bana: Sıradaki, then missed, the rest due first and the "iptal etti" rows under "Sonra". */
@Composable
internal fun ColumnScope.MineTab(
    state: HomeUiState,
    callbacks: HomeTaskCallbacks,
) {
    val nextUp = state.nextUp
    val issues = state.issues
    if (nextUp != null) {
        SectionLabel(R.string.home_next_up)
        NextUpCard(
            nextUp,
            onOpen = { callbacks.onOpenTask(nextUp.taskId, false) },
            photo = state.nextUpPhoto,
            onOpenUnsentPhoto = callbacks.onOpenUnsentPhoto,
        )
    }
    val rest = issues.missed.isNotEmpty() || state.later.isNotEmpty() || issues.cancelled.isNotEmpty()
    if (rest && nextUp != null) SectionLabel(R.string.home_later)
    issues.missed.forEach { MissedRow(it, state.myName) { callbacks.onOpenTask(it.taskId, false) } }
    state.later.forEach { LaterRow(it, state.myName) { callbacks.onOpenTask(it.taskId, false) } }
    issues.cancelled.forEach { CancelledRow(it) { callbacks.onDismissCancelled(it.taskId) } }
    if (issues.missed.isNotEmpty()) Footnote(R.string.issues_footer)
    // Texts written offline: only the server knows whom they are for, so both tabs show them (6a).
    state.queued.forEach { QueuedRow(it, state.myName) }
    if (nextUp == null && !rest && state.queued.isEmpty()) EmptyTasks(Modifier.padding(top = DurtTheme.dimens.space48))
}

/** Gönderdiklerim: what waits for my answer, then what is still going, then where finished ones are. */
@Composable
internal fun ColumnScope.SentTab(
    state: HomeUiState,
    callbacks: HomeTaskCallbacks,
) {
    val fresh = state.undo?.taskId
    val (approvals, going) = state.sent.partition { it.awaitingApproval }
    val waiting = state.issues.sent.isNotEmpty() || approvals.isNotEmpty()
    if (waiting) {
        SectionLabel(R.string.home_waiting)
        state.issues.sent.forEach { SentIssueRow(it) { callbacks.onOpenTask(it.taskId, false) } }
        approvals.forEach { SentRow(it, fresh) { callbacks.onOpenTask(it.taskId, true) } }
    }
    if (going.isNotEmpty() || state.queued.isNotEmpty()) {
        SectionLabel(R.string.home_ongoing)
        state.queued.forEach { QueuedRow(it, state.myName) }
        going.forEach { SentRow(it, fresh) { callbacks.onOpenTask(it.taskId, false) } }
    }
    if (waiting || going.isNotEmpty()) {
        Footnote(R.string.home_sent_footer)
    } else if (state.queued.isEmpty()) {
        Text(
            stringResource(R.string.home_sent_empty),
            style = DurtTheme.type.body,
            color = DurtTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = DurtTheme.dimens.space32),
        )
    }
}

@Composable
private fun Footnote(text: Int) {
    Text(
        stringResource(text),
        style = DurtTheme.type.caption,
        color = DurtTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = DurtTheme.dimens.space4),
    )
}

/** Gönderdiklerim with a dropped task on top (Sibel's side of 5d). */
@PreviewScreens
@Composable
private fun HomeScreenSentTabPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Sibel",
                    connected = true,
                    tab = HomeTab.Sent,
                    sent =
                        listOf(
                            SentUi("t2", "Çöpü at", null, DeadlineText.Today("20:00"), false, "eren", "Eren"),
                            SentUi("t3", "Temizlikçi için para çek", "1.000 TL", DeadlineText.Today("09:00"), true, "eren", "Eren", true),
                            SentUi(
                                "t4",
                                "Annemi ara, bayram için ne lazım sor",
                                null,
                                DeadlineText.Tomorrow("12:00"),
                                false,
                                "eren",
                                "Eren",
                            ),
                        ),
                    issues =
                        IssuesUi(
                            sent =
                                listOf(
                                    SentIssueUi(
                                        "t1",
                                        "Kuru temizlemeyi Moda'daki dükkandan al",
                                        "eren",
                                        "Eren",
                                        DeadlineText.Today("19:00"),
                                        SentIssue.Dropped,
                                        note = "Yarın hallederim",
                                    ),
                                ),
                        ),
                ),
            onBell = {},
            onProfile = {},
            onInvite = {},
            onComposeTextChange = {},
            onComposeSend = {},
            onVoiceStart = {},
            onVoiceEnd = {},
        )
    }
}
