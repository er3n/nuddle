package app.nuddle.ui.screens.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.api.model.EdgeType
import app.nuddle.domain.ConnectedFor
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.ChoiceChip
import app.nuddle.ui.components.DurtBottomSheet
import app.nuddle.ui.components.DurtBottomSheetContent
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.GroupDivider
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.components.LineRow
import app.nuddle.ui.components.PersonAvatar
import app.nuddle.ui.components.SettingsGroup
import app.nuddle.ui.components.SheetAction
import app.nuddle.ui.components.ToggleRow
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.screens.invite.mine
import app.nuddle.ui.screens.profile.yours
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.theme.avatarFor

@Composable
private fun PersonUiState.sinceLine(): String {
    val rel = stringResource(relationship.yours)
    return when (val s = since) {
        null -> rel
        ConnectedFor.Today -> stringResource(R.string.person_since_today, rel)
        is ConnectedFor.Days -> pluralStringResource(R.plurals.person_since_days, s.value, rel, s.value)
        is ConnectedFor.Months -> pluralStringResource(R.plurals.person_since_months, s.value, rel, s.value)
        is ConnectedFor.Years -> pluralStringResource(R.plurals.person_since_years, s.value, rel, s.value)
    }
}

/**
 * 5b: who, since when, my relationship to them and my switch for their tasks, how many tasks we wrote each other, and
 * "Bağlantıyı kaldır". The percentage of the design is not shown (open-questions#4: never a scorecard).
 */
@Composable
fun PersonScreen(
    state: PersonUiState,
    callbacks: PersonCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.background,
        modifier = modifier.testTag(TestTags.PERSON_SCREEN),
        bottom = {
            DurtButton(
                text = stringResource(R.string.person_remove),
                onClick = callbacks.onRemove,
                kind = DurtButtonKind.DangerOutline,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.PERSON_REMOVE),
            )
            Text(
                stringResource(R.string.person_remove_note, state.name),
                style = DurtTheme.type.caption,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        },
    ) {
        BackBar(title = state.name, onBack = callbacks.onBack)
        Column(
            Modifier.fillMaxWidth().padding(top = dimens.space8, bottom = dimens.space4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.space8),
        ) {
            PersonAvatar(state.personId, state.name, colors.avatarFor(state.personId), dimens.avatarHero)
            Text(state.sinceLine(), style = DurtTheme.type.bodySmall, color = colors.textSecondary, textAlign = TextAlign.Center)
        }
        if (state.failed) InlineAlert(stringResource(R.string.profile_failed_title), stringResource(R.string.profile_failed_body))
        SettingsGroup(stringResource(R.string.person_this_connection)) {
            LineRow(
                label = stringResource(R.string.person_relationship),
                value = stringResource(state.relationship.yours),
                onClick = callbacks.onRelationship,
                modifier = Modifier.testTag(TestTags.PERSON_RELATIONSHIP),
            )
            GroupDivider()
            ToggleRow(
                label = stringResource(R.string.person_notify_me),
                description = stringResource(R.string.person_notify_me_body, state.name),
                checked = state.notifyMe,
                onCheckedChange = callbacks.onNotifyMe,
                modifier = Modifier.testTag(TestTags.PERSON_NOTIFY_ME),
            )
        }
        SettingsGroup(stringResource(R.string.person_together)) {
            LineRow(
                label = stringResource(R.string.person_written),
                value = pluralStringResource(R.plurals.person_tasks, state.tasks, state.tasks),
            )
        }
    }
    state.sheet?.let { sheet ->
        val parts = partsOf(state, sheet, callbacks)
        DurtBottomSheet(parts.title, parts.description, callbacks.onDismiss, parts.primary, content = parts.content)
    }
}

private class SheetParts(
    val title: String,
    val description: String?,
    val primary: SheetAction,
    val content: @Composable ColumnScope.() -> Unit,
)

@Composable
private fun partsOf(
    state: PersonUiState,
    sheet: PersonSheet,
    callbacks: PersonCallbacks,
): SheetParts {
    val failed: @Composable ColumnScope.() -> Unit = {
        if (sheet.failed) InlineAlert(stringResource(R.string.profile_failed_title), stringResource(R.string.profile_failed_body))
    }
    return when (sheet) {
        is PersonSheet.Relationship -> {
            SheetParts(
                title = stringResource(R.string.invite_relationship_question_named, state.name),
                description = null,
                primary =
                    SheetAction(
                        stringResource(R.string.action_save),
                        callbacks.onConfirm,
                        enabled = !sheet.busy,
                        loading = sheet.busy,
                    ),
            ) {
                RelationshipChips(sheet.selected, callbacks.onSelect)
                failed()
            }
        }

        is PersonSheet.Remove -> {
            SheetParts(
                title = stringResource(R.string.person_remove_title),
                description = stringResource(R.string.person_remove_note, state.name),
                primary =
                    SheetAction(
                        stringResource(R.string.person_remove),
                        callbacks.onConfirm,
                        destructive = true,
                        enabled = !sheet.busy,
                        loading = sheet.busy,
                    ),
            ) { failed() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class) // Reason: FlowRow is the layout for wrapping chips; no stable alternative.
@Composable
private fun RelationshipChips(
    selected: EdgeType,
    onSelect: (EdgeType) -> Unit,
) {
    val dimens = DurtTheme.dimens
    FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.space10), verticalArrangement = Arrangement.spacedBy(dimens.space10)) {
        EdgeType.entries.forEach { type ->
            ChoiceChip(
                text = stringResource(type.mine),
                selected = type == selected,
                onClick = { onSelect(type) },
                modifier = Modifier.testTag(TestTags.personRelationship(type.value)),
            )
        }
    }
}

internal val previewPerson =
    PersonUiState(
        personId = "sibel",
        name = "Sibel",
        relationship = EdgeType.SPOUSE,
        since = ConnectedFor.Months(3),
        notifyMe = true,
        tasks = 24,
    )

@PreviewScreens
@Composable
private fun PersonScreenPreview() {
    DurtTheme { PersonScreen(previewPerson, PersonCallbacks()) }
}

@PreviewScreens
@Composable
private fun PersonScreenNewFailedPreview() {
    DurtTheme {
        PersonScreen(
            previewPerson.copy(relationship = EdgeType.OTHER, since = ConnectedFor.Today, notifyMe = false, tasks = 1, failed = true),
            PersonCallbacks(),
        )
    }
}

@Composable
private fun PersonSheetPreviewOf(sheet: PersonSheet) {
    DurtTheme {
        // The sheet is a ModalBottomSheet window; screenshots show its content over the screen behind the scrim.
        Box(Modifier.fillMaxSize().background(DurtTheme.colors.background), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxSize().background(DurtTheme.colors.scrim))
            val parts = partsOf(previewPerson, sheet, PersonCallbacks())
            DurtBottomSheetContent(parts.title, parts.description, onDismiss = {}, primary = parts.primary, content = parts.content)
        }
    }
}

@PreviewScreens
@Composable
private fun RelationshipSheetPreview() {
    PersonSheetPreviewOf(PersonSheet.Relationship(EdgeType.SPOUSE))
}

@PreviewScreens
@Composable
private fun RemoveSheetPreview() {
    PersonSheetPreviewOf(PersonSheet.Remove())
}

@PreviewScreens
@Composable
private fun RemoveSheetFailedPreview() {
    PersonSheetPreviewOf(PersonSheet.Remove(failed = true))
}
