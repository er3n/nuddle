package app.nuddle.ui.screens.consent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import app.nuddle.R
import app.nuddle.api.model.EdgeType
import app.nuddle.domain.Ago
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.Avatar
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.ChoiceChip
import app.nuddle.ui.components.DurtBottomSheet
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.GroupDivider
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.components.SheetAction
import app.nuddle.ui.components.TextLink
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.screens.agoText
import app.nuddle.ui.screens.invite.mine
import app.nuddle.ui.theme.DurtTheme

@Immutable
data class ConsentCallbacks(
    val onAllow: () -> Unit = {},
    val onLater: () -> Unit = {},
    val onDecline: () -> Unit = {},
    val onChangeRelationship: () -> Unit = {},
    val onPick: (EdgeType) -> Unit = {},
    val onPickDismiss: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** "eşi", "çocuğu" ...: what I am to the inviter. Null for "Diğer", which reads "Selin seni ekledi." */
private val EdgeType.theirs: Int?
    get() =
        when (this) {
            EdgeType.SPOUSE -> R.string.relationship_their_spouse
            EdgeType.PARENT -> R.string.relationship_their_parent
            EdgeType.CHILD -> R.string.relationship_their_child
            EdgeType.SIBLING -> R.string.relationship_their_sibling
            EdgeType.ROOMMATE -> R.string.relationship_their_roommate
            EdgeType.OTHER -> null
        }

/**
 * 0f and 1d: the social consent. Accepting is also the technical one: setup follows (docs/product.md, Permissions
 * and setup). Declining is never reported to the inviter.
 */
@Composable
fun ConsentScreen(
    state: ConsentUiState,
    callbacks: ConsentCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.background,
        modifier = modifier.testTag(TestTags.CONSENT),
        horizontalAlignment = Alignment.CenterHorizontally,
        bottom = { Answers(state, callbacks) },
    ) {
        if (state.fromBell) {
            BackBar(title = null, onBack = callbacks.onBack)
            Text(
                stringResource(R.string.consent_detail_eyebrow, agoText(state.ago)),
                style = DurtTheme.type.captionStrong,
                color = colors.textTertiary,
            )
        }
        Avatar(
            state.inviterName,
            colors.avatarOthers.first(),
            dimens.avatarHero,
            Modifier.padding(top = if (state.fromBell) dimens.space8 else dimens.space48),
        )
        Text(
            stringResource(R.string.consent_title, state.inviterName),
            style = DurtTheme.type.h1,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        RelationshipRow(state, callbacks.onChangeRelationship)
        Promises(state)
        if (state.gone) {
            InlineAlert(stringResource(R.string.consent_gone_title), stringResource(R.string.consent_gone_body))
        } else if (state.failed) {
            InlineAlert(stringResource(R.string.consent_failed_title), stringResource(R.string.invite_failed_body))
        }
    }
    if (state.picking) RelationshipSheet(state, callbacks)
}

@Composable
private fun ColumnScope.Answers(
    state: ConsentUiState,
    callbacks: ConsentCallbacks,
) {
    val colors = DurtTheme.colors
    DurtButton(
        text = stringResource(R.string.consent_allow),
        onClick = callbacks.onAllow,
        kind = DurtButtonKind.Accent,
        enabled = !state.gone,
        loading = state.sending,
        modifier = Modifier.testTag(TestTags.CONSENT_ALLOW),
    )
    if (state.fromBell) {
        DurtButton(
            text = stringResource(R.string.consent_decline),
            onClick = callbacks.onDecline,
            kind = DurtButtonKind.Ghost,
            enabled = !state.gone && !state.sending,
            modifier = Modifier.testTag(TestTags.CONSENT_DECLINE),
        )
        Text(stringResource(R.string.consent_decline_note), style = DurtTheme.type.caption, color = colors.textSecondary)
    } else {
        DurtButton(
            text = stringResource(R.string.consent_later),
            onClick = callbacks.onLater,
            kind = DurtButtonKind.Ghost,
            modifier = Modifier.testTag(TestTags.CONSENT_LATER),
        )
    }
}

/** "Selin seni **eşi** olarak ekledi." with Değiştir; my own pick shows once it differs from the inviter's. */
@Composable
private fun RelationshipRow(
    state: ConsentUiState,
    onChange: () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    val role = state.addedAs.theirs?.let { stringResource(it) }
    val sentence =
        if (role == null) {
            buildAnnotatedString { append(stringResource(R.string.consent_added, state.inviterName)) }
        } else {
            val full = stringResource(R.string.consent_added_as, state.inviterName, role)
            val start = full.indexOf(role)
            buildAnnotatedString {
                append(full)
                if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + role.length)
            }
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(dimens.borderCard, colors.border, shape)
                .padding(horizontal = dimens.space16, vertical = dimens.space10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(sentence, style = DurtTheme.type.body, color = colors.ink)
            if (state.myPick != state.addedAs.inverse()) {
                Text(
                    stringResource(R.string.consent_my_pick, stringResource(state.myPick.mine)),
                    style = DurtTheme.type.caption,
                    color = colors.textSecondary,
                )
            }
        }
        TextLink(
            text = stringResource(R.string.consent_change),
            onClick = onChange,
            style = DurtTheme.type.bodyStrong,
            modifier = Modifier.testTag(TestTags.CONSENT_CHANGE),
        )
    }
}

@Composable
private fun Promises(state: ConsentUiState) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    val lines =
        listOf(
            stringResource(R.string.consent_promise_tasks),
            stringResource(R.string.consent_promise_full_screen),
            stringResource(R.string.consent_promise_quiet, state.quietStart, state.quietEnd),
            stringResource(R.string.consent_promise_reach_out, state.inviterName),
            stringResource(R.string.consent_promise_remove),
        )
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(dimens.borderCard, colors.border, shape)
            .padding(horizontal = dimens.space16),
    ) {
        lines.forEachIndexed { index, line ->
            if (index > 0) GroupDivider()
            Row(
                Modifier.padding(vertical = dimens.space14),
                horizontalArrangement = Arrangement.spacedBy(dimens.space14),
            ) {
                Icon(DurtIcons.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(dimens.icon))
                Text(line, style = DurtTheme.type.body, color = colors.ink)
            }
        }
    }
}

/** "Değiştir": what the inviter is to me. */
@OptIn(ExperimentalLayoutApi::class) // Reason: FlowRow is the layout for wrapping chips; no stable alternative.
@Composable
private fun RelationshipSheet(
    state: ConsentUiState,
    callbacks: ConsentCallbacks,
) {
    val dimens = DurtTheme.dimens
    DurtBottomSheet(
        title = stringResource(R.string.consent_pick_title, state.inviterName),
        description = null,
        onDismiss = callbacks.onPickDismiss,
        primary = SheetAction(stringResource(R.string.action_ok), callbacks.onPickDismiss),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.space10), verticalArrangement = Arrangement.spacedBy(dimens.space10)) {
            EdgeType.entries.forEach { type ->
                ChoiceChip(stringResource(type.mine), selected = type == state.myPick, onClick = { callbacks.onPick(type) })
            }
        }
    }
}

private val previewState =
    ConsentUiState(
        inviterName = "Selin",
        addedAs = EdgeType.SPOUSE,
        myPick = EdgeType.SPOUSE,
        quietStart = "23:00",
        quietEnd = "07:00",
    )

@PreviewScreens
@Composable
private fun ConsentScreenPreview() {
    DurtTheme { ConsentScreen(previewState, ConsentCallbacks()) }
}

@PreviewScreens
@Composable
private fun ConsentScreenFromBellPreview() {
    DurtTheme {
        ConsentScreen(
            previewState.copy(fromBell = true, ago = Ago.Hours(2), addedAs = EdgeType.CHILD, myPick = EdgeType.SIBLING),
            ConsentCallbacks(),
        )
    }
}

@PreviewScreens
@Composable
private fun ConsentScreenGonePreview() {
    DurtTheme { ConsentScreen(previewState.copy(gone = true), ConsentCallbacks()) }
}
