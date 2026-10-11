package app.nuddle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme

private const val BADGE_MAX = 9

/** The logo: "dürt" with the dot in `accent` (TopBar, welcome). Follows the locale (open-questions#11). */
@Composable
fun DurtLogo(
    modifier: Modifier = Modifier,
    color: Color = DurtTheme.colors.ink,
    style: TextStyle = DurtTheme.type.brand,
) {
    val accent = DurtTheme.colors.accent
    val text = stringResource(R.string.logo_text)
    Text(
        text =
            buildAnnotatedString {
                append(text)
                withStyle(SpanStyle(color = accent)) { append(".") }
            },
        modifier = modifier,
        color = color,
        style = style,
    )
}

/** A 44 dp white circle with an icon: back button, bell. */
@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    description: String,
    testTag: String,
    content: @Composable () -> Unit,
) {
    val dimens = DurtTheme.dimens
    Box(
        modifier =
            Modifier
                .size(dimens.minTouchTarget)
                .clip(CircleShape)
                .background(DurtTheme.colors.surface)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Home top bar (docs/ux/components.md, 2): logo left, bell with unread badge and the user's avatar right.
 * Scrolls with the content.
 */
@Composable
fun TopBar(
    myName: String,
    unread: Int,
    onBell: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val bellDescription =
        if (unread > 0) pluralStringResource(R.plurals.cd_bell_unread, unread, unread) else stringResource(R.string.cd_bell)
    val profileDescription = stringResource(R.string.cd_profile)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space8),
    ) {
        DurtLogo(Modifier.weight(1f))
        Box {
            CircleIconButton(onClick = onBell, description = bellDescription, testTag = TestTags.BELL) {
                Icon(
                    DurtIcons.Bell,
                    contentDescription = null,
                    tint = colors.ink,
                    modifier = Modifier.size(dimens.iconLarge),
                )
            }
            if (unread > 0) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = dimens.space4, y = -dimens.space4)
                            .sizeIn(minWidth = dimens.badgeSize, minHeight = dimens.badgeSize)
                            .border(dimens.borderFocused, colors.background, CircleShape)
                            .padding(dimens.borderFocused)
                            .background(colors.poke, CircleShape)
                            .padding(horizontal = dimens.space4),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (unread > BADGE_MAX) stringResource(R.string.badge_overflow) else unread.toString(),
                        style = DurtTheme.type.micro,
                        color = colors.surface,
                    )
                }
            }
        }
        Avatar(
            name = myName,
            color = colors.avatarSelf,
            size = dimens.avatarTopBar,
            modifier =
                Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onProfile)
                    .semantics { contentDescription = profileDescription }
                    .testTag(TestTags.PROFILE),
        )
    }
}

/** Back button and optional `h3` title (docs/ux/components.md, 2). No title means button only. */
@Composable
fun BackBar(
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val dimens = DurtTheme.dimens
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        CircleIconButton(onClick = onBack, description = stringResource(R.string.cd_back), testTag = TestTags.BACK) {
            Icon(
                DurtIcons.Back,
                contentDescription = null,
                tint = DurtTheme.colors.ink,
                modifier = Modifier.size(dimens.iconLarge),
            )
        }
        if (title != null) {
            Text(text = title, style = DurtTheme.type.h3, color = DurtTheme.colors.ink, modifier = Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f))
        }
        trailing?.invoke()
    }
}

@PreviewComponent
@Composable
private fun BarsPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space16)) {
            DurtLogo()
            TopBar(myName = "Eren", unread = 1, onBell = {}, onProfile = {})
            TopBar(myName = "Eren", unread = 12, onBell = {}, onProfile = {})
            BackBar(title = "Bildirimler", onBack = {})
            BackBar(title = null, onBack = {})
        }
    }
}
