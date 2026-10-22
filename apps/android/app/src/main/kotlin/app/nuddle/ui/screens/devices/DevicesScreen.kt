package app.nuddle.ui.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.GroupDivider
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.components.SettingsGroup
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.theme.DurtTheme

/** "Son görülme bugün 14:32", "Son görülme dün 09:10", "Son görülme 12 Ekim 18:05". */
@Composable
private fun WrittenText.seen(): String =
    when (this) {
        is WrittenText.Today -> stringResource(R.string.devices_seen_today, time)
        is WrittenText.Yesterday -> stringResource(R.string.devices_seen_yesterday, time)
        is WrittenText.OnDate -> stringResource(R.string.devices_seen_on, day, stringArrayResource(R.array.month_names)[month - 1], time)
    }

/** "Sürüm 0.1.7 · Son görülme bugün 14:32"; this phone has no time, it is in my hand. */
@Composable
private fun PhoneRowUi.caption(): String {
    val parts =
        listOfNotNull(
            appVersion?.let { stringResource(R.string.devices_version, it) },
            lastSeen.seen().takeUnless { current },
        )
    return parts.joinToString(stringResource(R.string.devices_separator))
}

/**
 * 5e Cihazlarım (NUD-142, ADR 0027): the phones my account is open on, newest first, and "Diğer cihazlardan çıkış yap"
 * at the bottom when there is another one. No canvas: built from the parts of 5a and 5b (screens.md).
 */
@Composable
fun DevicesScreen(
    state: DevicesUiState,
    callbacks: DevicesCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.background,
        modifier = modifier.testTag(TestTags.DEVICES_SCREEN),
        bottom = {
            if (state.canSignOutOthers) {
                DurtButton(
                    text = stringResource(R.string.devices_sign_out_others),
                    onClick = callbacks.onSignOutOthers,
                    kind = DurtButtonKind.DangerOutline,
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.DEVICES_SIGN_OUT_OTHERS),
                )
                Text(
                    stringResource(R.string.devices_sign_out_others_note),
                    style = DurtTheme.type.caption,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        BackBar(title = stringResource(R.string.devices_title), onBack = callbacks.onBack)
        when (val list = state.list) {
            PhonesList.Loading -> {
                CircularProgressIndicator(
                    color = colors.ink,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(dimens.space16),
                )
            }

            PhonesList.Failed -> {
                InlineAlert(stringResource(R.string.devices_failed_title), stringResource(R.string.profile_failed_body))
                DurtButton(
                    text = stringResource(R.string.generic_error_retry),
                    onClick = callbacks.onRetry,
                    kind = DurtButtonKind.Secondary,
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.DEVICES_RETRY),
                )
            }

            is PhonesList.Shown -> {
                SettingsGroup(stringResource(R.string.devices_group)) {
                    list.phones.forEachIndexed { i, phone ->
                        if (i > 0) GroupDivider()
                        PhoneRow(phone)
                    }
                }
            }
        }
    }
    state.sheet?.let { SignOutOthersSheetView(it, callbacks) }
}

/** The model over the version and last seen, as the text of a ToggleRow; "Bu telefon" on the right of mine. */
@Composable
private fun PhoneRow(phone: PhoneRowUi) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.listRowWithDescription)
                .padding(vertical = dimens.space10)
                .testTag(TestTags.DEVICES_PHONE),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(phone.model ?: stringResource(R.string.devices_unknown_model), style = DurtTheme.type.bodyStrong, color = colors.ink)
            val caption = phone.caption()
            if (caption.isNotEmpty()) Text(caption, style = DurtTheme.type.caption, color = colors.textSecondary)
        }
        if (phone.current) {
            Text(stringResource(R.string.devices_this_phone), style = DurtTheme.type.bodySmallStrong, color = colors.success)
        }
    }
}

internal val previewPhones =
    listOf(
        PhoneRowUi("Google Pixel 8", "0.1.7", WrittenText.Today("14:32"), current = true),
        PhoneRowUi("Samsung SM-S911B", "0.1.6", WrittenText.Yesterday("09:10"), current = false),
        PhoneRowUi(null, null, WrittenText.OnDate(12, 10, "18:05"), current = false),
    )

@PreviewScreens
@Composable
private fun DevicesLoadingPreview() {
    DurtTheme { DevicesScreen(DevicesUiState(PhonesList.Loading), DevicesCallbacks()) }
}

@PreviewScreens
@Composable
private fun DevicesOnePhonePreview() {
    DurtTheme { DevicesScreen(DevicesUiState(PhonesList.Shown(previewPhones.take(1))), DevicesCallbacks()) }
}

@PreviewScreens
@Composable
private fun DevicesSeveralPreview() {
    DurtTheme { DevicesScreen(DevicesUiState(PhonesList.Shown(previewPhones)), DevicesCallbacks()) }
}

@PreviewScreens
@Composable
private fun DevicesFailedPreview() {
    DurtTheme { DevicesScreen(DevicesUiState(PhonesList.Failed), DevicesCallbacks()) }
}
