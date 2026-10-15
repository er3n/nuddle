package app.nuddle.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.nuddle.R
import app.nuddle.api.model.EdgeType
import app.nuddle.domain.GrantedPermissions
import app.nuddle.domain.SetupStep
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.Avatar
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.GroupDivider
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.components.LineRow
import app.nuddle.ui.components.MY_AVATAR
import app.nuddle.ui.components.PersonAvatar
import app.nuddle.ui.components.SettingsGroup
import app.nuddle.ui.components.TextLink
import app.nuddle.ui.components.ToggleRow
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.screens.home.asYours
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.theme.avatarFor
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import kotlinx.datetime.format.char

private val HHMM =
    LocalTime.Format {
        hour()
        char(':')
        minute()
    }

internal fun LocalTime.hhmm(): String = format(HHMM)

/** "Eşin", "Annen / Baban" ... and "Diğer". */
internal val EdgeType.yours: Int get() = asYours ?: R.string.relationship_other

/**
 * 5a: who I am, "Bizimkiler", the two human channel switches, quiet hours, permissions, Çıkış yap and Hesabı sil.
 * The settings sheets (rename, 7g, 7h, 7i) open on top.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    callbacks: ProfileCallbacks,
    modifier: Modifier = Modifier,
    sheetCallbacks: ProfileSheetCallbacks = ProfileSheetCallbacks(),
) {
    val dimens = DurtTheme.dimens
    ScreenLayout(background = DurtTheme.colors.background, modifier = modifier.testTag(TestTags.PROFILE_SCREEN)) {
        BackBar(title = stringResource(R.string.profile_title), onBack = callbacks.onBack)
        Me(state, callbacks.onEditName, callbacks.onPicture)
        if (state.failed) InlineAlert(stringResource(R.string.profile_failed_title), stringResource(R.string.profile_failed_body))
        People(state, callbacks)
        SettingsGroup(stringResource(R.string.profile_poking)) {
            ToggleRow(
                label = stringResource(R.string.profile_use_connections),
                description = stringResource(R.string.profile_use_connections_body),
                checked = state.useConnectionsForMyTasks,
                onCheckedChange = callbacks.onUseConnections,
                modifier = Modifier.testTag(TestTags.PROFILE_USE_CONNECTIONS),
            )
            GroupDivider()
            ToggleRow(
                label = stringResource(R.string.profile_notify_me),
                description = stringResource(R.string.profile_notify_me_body),
                checked = state.notifyMeForOthersTasks,
                onCheckedChange = callbacks.onNotifyMe,
                modifier = Modifier.testTag(TestTags.PROFILE_NOTIFY_ME),
            )
        }
        SettingsGroup(stringResource(R.string.profile_quiet_hours)) {
            LineRow(
                label = stringResource(R.string.profile_quiet_row),
                value = stringResource(R.string.profile_quiet_range, state.quietStart.hhmm(), state.quietEnd.hhmm()),
                onClick = callbacks.onQuietHours,
                modifier = Modifier.testTag(TestTags.PROFILE_QUIET),
            )
        }
        Permissions(state.permissions, callbacks.onFixPermission)
        Row(Modifier.fillMaxWidth().padding(horizontal = dimens.space4), horizontalArrangement = Arrangement.SpaceBetween) {
            TextLink(
                text = stringResource(R.string.profile_log_out),
                onClick = callbacks.onLogOut,
                color = DurtTheme.colors.ink,
                style = DurtTheme.type.bodySmallStrong,
                modifier = Modifier.testTag(TestTags.PROFILE_LOG_OUT),
            )
            TextLink(
                text = stringResource(R.string.profile_delete),
                onClick = callbacks.onDelete,
                color = DurtTheme.colors.danger,
                style = DurtTheme.type.bodySmallStrong,
                modifier = Modifier.testTag(TestTags.PROFILE_DELETE),
            )
        }
    }
    state.sheet?.let { ProfileSheetView(it, state.people.isEmpty(), sheetCallbacks) }
}

@Composable
private fun Me(
    state: ProfileUiState,
    onEditName: () -> Unit,
    onPicture: () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val changePicture = stringResource(R.string.cd_profile_picture)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = dimens.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space14),
    ) {
        PersonAvatar(
            MY_AVATAR,
            state.name,
            colors.avatarSelf,
            dimens.avatarProfile,
            modifier =
                Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = changePicture, role = Role.Button, onClick = onPicture)
                    .testTag(TestTags.PROFILE_PICTURE),
        )
        Column(Modifier.weight(1f)) {
            Text(state.name, style = DurtTheme.type.brand, color = colors.ink)
            Text(state.phone, style = DurtTheme.type.caption, color = colors.textSecondary)
        }
        TextLink(
            text = stringResource(R.string.profile_edit),
            onClick = onEditName,
            style = DurtTheme.type.bodySmallStrong,
            modifier = Modifier.testTag(TestTags.PROFILE_EDIT),
        )
    }
}

/** Active connections, then invites still open, then "+ Birini davet et". */
@Composable
private fun People(
    state: ProfileUiState,
    callbacks: ProfileCallbacks,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    SettingsGroup(stringResource(R.string.profile_people)) {
        state.people.forEach { person ->
            LineRow(
                label = person.name,
                value = stringResource(person.relationship.yours),
                leading = { PersonAvatar(person.personId, person.name, colors.avatarFor(person.personId), dimens.avatarInline) },
                onClick = { callbacks.onOpenPerson(person.personId) },
                modifier = Modifier.testTag(TestTags.profilePerson(person.name)),
            )
            GroupDivider()
        }
        state.pending.forEach { name ->
            LineRow(
                label = name,
                value = stringResource(R.string.profile_pending),
                valueColor = colors.textTertiary,
                leading = { Avatar(name, colors.textMuted, dimens.avatarInline) },
            )
            GroupDivider()
        }
        LineRow(
            label = stringResource(R.string.profile_invite),
            labelColor = colors.link,
            labelStyle = DurtTheme.type.bodyStrong,
            onClick = callbacks.onInvite,
            modifier = Modifier.testTag(TestTags.PROFILE_INVITE),
        )
    }
}

@Composable
private fun Permissions(
    granted: GrantedPermissions,
    onFix: (SetupStep) -> Unit,
) {
    SettingsGroup(stringResource(R.string.profile_permissions)) {
        granted.steps.forEachIndexed { i, step ->
            if (i > 0) GroupDivider()
            val label =
                when (step) {
                    SetupStep.NOTIFICATIONS -> R.string.profile_permission_notifications
                    SetupStep.EXACT_ALARM -> R.string.profile_permission_alarms
                    SetupStep.FULL_SCREEN -> R.string.profile_permission_full_screen
                    SetupStep.BATTERY -> R.string.profile_permission_battery
                }
            val tag = Modifier.testTag(TestTags.profilePermission(step.name))
            if (granted.has(step)) {
                LineRow(stringResource(label), trailing = { GrantedMark() }, modifier = tag)
            } else {
                LineRow(
                    label = stringResource(label),
                    value = stringResource(R.string.profile_permission_off),
                    valueColor = DurtTheme.colors.warning,
                    onClick = { onFix(step) },
                    modifier = tag,
                )
            }
        }
    }
}

/** A check on `successSoft`, as in the permission rows of 5a. */
@Composable
private fun GrantedMark() {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val on = stringResource(R.string.cd_permission_on)
    Box(
        Modifier
            .size(dimens.iconLarge)
            .background(colors.successSoft, CircleShape)
            .semantics { contentDescription = on },
        contentAlignment = Alignment.Center,
    ) {
        Icon(DurtIcons.Check, contentDescription = null, tint = colors.onSuccessSoft, modifier = Modifier.size(dimens.iconSmall))
    }
}

internal val previewProfile =
    ProfileUiState(
        name = "Eren",
        phone = "+90 532 123 45 67",
        people = listOf(PersonRowUi("sibel", "Sibel", EdgeType.SPOUSE)),
        pending = emptyList(),
        useConnectionsForMyTasks = true,
        notifyMeForOthersTasks = true,
        quietStart = LocalTime(23, 0),
        quietEnd = LocalTime(7, 0),
        permissions = GrantedPermissions(notifications = true, exactAlarm = true, fullScreen = false),
    )

@PreviewScreens
@Composable
private fun ProfileScreenPreview() {
    DurtTheme { ProfileScreen(previewProfile, ProfileCallbacks()) }
}

@PreviewScreens
@Composable
private fun ProfileScreenAloneFailedPreview() {
    DurtTheme {
        ProfileScreen(
            previewProfile.copy(
                people = emptyList(),
                pending = listOf("Defne"),
                useConnectionsForMyTasks = false,
                permissions = GrantedPermissions(notifications = false, exactAlarm = false, fullScreen = true),
                failed = true,
            ),
            ProfileCallbacks(),
        )
    }
}
