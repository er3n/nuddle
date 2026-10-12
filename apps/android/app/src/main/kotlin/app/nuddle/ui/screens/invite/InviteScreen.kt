package app.nuddle.ui.screens.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.api.model.EdgeType
import app.nuddle.domain.TurkishPhone
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.Avatar
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.ChoiceChip
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.DurtButtonSize
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.components.TextLink
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.BigTextField
import app.nuddle.ui.screens.FieldLabel
import app.nuddle.ui.screens.NoteCard
import app.nuddle.ui.screens.PhoneGrouping
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.theme.DurtTheme

@Immutable
data class InviteCallbacks(
    val onBack: () -> Unit = {},
    val onPickContact: () -> Unit = {},
    val onTypeNumber: () -> Unit = {},
    val onTypedName: (String) -> Unit = {},
    val onTypedNumber: (String) -> Unit = {},
    val onRelationship: (EdgeType) -> Unit = {},
    val onSend: () -> Unit = {},
)

private const val DASH = 8f

/** 1e and 1f: who (a single contact, or a number typed by hand) and what they are to me, then the share sheet. */
@Composable
fun InviteScreen(
    state: InviteUiState,
    callbacks: InviteCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.background,
        modifier = modifier.testTag(TestTags.INVITE),
        bottom = {
            DurtButton(
                text = stringResource(R.string.invite_send),
                onClick = callbacks.onSend,
                kind = DurtButtonKind.Accent,
                enabled = state.invitee != null && state.relationship != null,
                loading = state.sending,
                modifier = Modifier.testTag(TestTags.INVITE_SEND),
            )
            if (state.invitee == null || state.relationship == null) {
                Text(
                    stringResource(R.string.invite_send_hint),
                    style = DurtTheme.type.caption,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        },
    ) {
        BackBar(title = stringResource(R.string.invite_title), onBack = callbacks.onBack)
        FieldLabel(stringResource(R.string.invite_who))
        Who(state, callbacks)
        FieldLabel(
            state.invitee?.let { stringResource(R.string.invite_relationship_question_named, it.name) }
                ?: stringResource(R.string.invite_relationship_question),
        )
        RelationshipChips(state.relationship, callbacks.onRelationship)
        if (state.relationship == null) {
            Text(stringResource(R.string.invite_relationship_required), style = DurtTheme.type.caption, color = colors.textSecondary)
        } else {
            NoteCard {
                Text(stringResource(R.string.invite_relationship_why), style = DurtTheme.type.bodySmall, color = colors.textBody)
            }
        }
        if (state.failed) {
            InlineAlert(stringResource(R.string.invite_failed_title), stringResource(R.string.invite_failed_body))
        }
    }
}

@Composable
private fun ColumnScope.Who(
    state: InviteUiState,
    callbacks: InviteCallbacks,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val contact = state.contact
    when {
        contact != null -> {
            ContactCard(contact, onChange = callbacks.onPickContact)
            Text(stringResource(R.string.invite_single_contact_note), style = DurtTheme.type.caption, color = colors.textSecondary)
        }

        state.typing -> {
            BigTextField(
                value = state.typedName,
                onValueChange = callbacks.onTypedName,
                label = stringResource(R.string.invite_name_label),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag(TestTags.INVITE_NAME),
            )
            BigTextField(
                value = state.typedNumber,
                onValueChange = callbacks.onTypedNumber,
                label = stringResource(R.string.phone_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                visualTransformation = PhoneGrouping,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.INVITE_NUMBER),
            )
            TextLink(
                text = stringResource(R.string.invite_pick_contact),
                onClick = callbacks.onPickContact,
                style = DurtTheme.type.bodyStrong,
            )
        }

        else -> {
            PickContactCard(callbacks.onPickContact)
            if (state.numberInvalid) {
                Text(stringResource(R.string.invite_number_invalid), style = DurtTheme.type.caption, color = colors.danger)
            }
            TextLink(
                text = stringResource(R.string.invite_type_number),
                onClick = callbacks.onTypeNumber,
                style = DurtTheme.type.bodyStrong,
                modifier = Modifier.padding(top = dimens.space4).testTag(TestTags.INVITE_TYPE_NUMBER),
            )
        }
    }
}

/** 1e: the dashed card that opens the system contact picker for one contact. */
@Composable
private fun PickContactCard(onPick: () -> Unit) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(dimens.radiusL))
                .drawBehind {
                    val radius = dimens.radiusL.toPx()
                    drawRoundRect(
                        color = colors.textMuted,
                        cornerRadius = CornerRadius(radius, radius),
                        style =
                            Stroke(
                                width = dimens.borderStrong.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)),
                            ),
                    )
                }.clickable(role = Role.Button, onClick = onPick)
                .padding(dimens.space16)
                .testTag(TestTags.INVITE_PICK_CONTACT),
        horizontalArrangement = Arrangement.spacedBy(dimens.space14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(dimens.avatarTopBar).background(colors.ink, CircleShape), contentAlignment = Alignment.Center) {
            Icon(DurtIcons.PersonPlus, contentDescription = null, tint = colors.surface, modifier = Modifier.size(dimens.iconLarge))
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.invite_pick_contact),
                style = DurtTheme.type.bodyLarge.copy(fontWeight = DurtTheme.type.bodyStrong.fontWeight),
                color = colors.ink,
            )
            Text(stringResource(R.string.invite_pick_contact_body), style = DurtTheme.type.bodySmall, color = colors.textSecondary)
        }
    }
}

/** 1f: the chosen contact with "Değiştir", which opens the picker again. */
@Composable
private fun ContactCard(
    contact: Invitee,
    onChange: () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusL)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(dimens.borderFocused, colors.ink, shape)
                .padding(dimens.space16),
        horizontalArrangement = Arrangement.spacedBy(dimens.space14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact.name, colors.avatarOthers.first(), dimens.avatarTopBar)
        Column(Modifier.weight(1f)) {
            Text(contact.name, style = DurtTheme.type.bodyLarge.copy(fontWeight = DurtTheme.type.bodyStrong.fontWeight), color = colors.ink)
            Text(TurkishPhone.display(contact.phone), style = DurtTheme.type.bodySmall, color = colors.textSecondary)
        }
        DurtButton(
            text = stringResource(R.string.invite_change),
            onClick = onChange,
            kind = DurtButtonKind.Secondary,
            size = DurtButtonSize.Compact,
            modifier = Modifier.width(dimens.countryButtonWidth).testTag(TestTags.INVITE_PICK_CONTACT),
        )
    }
}

/** Nothing preselected: the inviter must choose (docs/product.md, Connections). */
@OptIn(ExperimentalLayoutApi::class) // Reason: FlowRow is the layout for wrapping chips; no stable alternative.
@Composable
private fun RelationshipChips(
    selected: EdgeType?,
    onSelect: (EdgeType) -> Unit,
) {
    val dimens = DurtTheme.dimens
    FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.space10), verticalArrangement = Arrangement.spacedBy(dimens.space10)) {
        EdgeType.entries.forEach { type ->
            ChoiceChip(
                text = stringResource(type.mine),
                selected = type == selected,
                onClick = { onSelect(type) },
                modifier = Modifier.testTag(TestTags.INVITE_RELATIONSHIP + type.value),
            )
        }
    }
}

/** "Eşim", "Annem / Babam" ...: what they are to me. */
internal val EdgeType.mine: Int
    get() =
        when (this) {
            EdgeType.SPOUSE -> R.string.relationship_spouse
            EdgeType.PARENT -> R.string.relationship_parent
            EdgeType.CHILD -> R.string.relationship_child
            EdgeType.SIBLING -> R.string.relationship_sibling
            EdgeType.ROOMMATE -> R.string.relationship_roommate
            EdgeType.OTHER -> R.string.relationship_other
        }

@PreviewScreens
@Composable
private fun InviteScreenEmptyPreview() {
    DurtTheme { InviteScreen(InviteUiState(), InviteCallbacks()) }
}

@PreviewScreens
@Composable
private fun InviteScreenReadyPreview() {
    DurtTheme {
        InviteScreen(
            InviteUiState(contact = Invitee("Selin", "+905329876543"), relationship = EdgeType.SPOUSE),
            InviteCallbacks(),
        )
    }
}

@PreviewScreens
@Composable
private fun InviteScreenTypingPreview() {
    DurtTheme {
        InviteScreen(
            InviteUiState(typing = true, typedName = "Selin", typedNumber = "53298", failed = true),
            InviteCallbacks(),
        )
    }
}
