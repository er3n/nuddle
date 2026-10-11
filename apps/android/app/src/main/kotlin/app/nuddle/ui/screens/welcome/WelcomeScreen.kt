package app.nuddle.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.Avatar
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.DurtLogo
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.theme.DurtTheme

/** 0a, or 0b when opened from an invite link (the inviter is shown on top). */
@Immutable
data class WelcomeUiState(
    val inviterName: String? = null,
    val inviterColor: Color? = null,
)

@Composable
fun WelcomeScreen(
    state: WelcomeUiState,
    onStart: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.darkBackground,
        modifier = modifier,
        horizontalPadding = dimens.space24,
        bottom = {
            DurtButton(
                text = stringResource(R.string.welcome_start),
                onClick = onStart,
                kind = DurtButtonKind.Accent,
                modifier = Modifier.testTag(TestTags.WELCOME_START),
            )
            TermsText(onTerms = onTerms, onPrivacy = onPrivacy)
        },
    ) {
        DurtLogo(color = colors.darkText, style = DurtTheme.type.h1, modifier = Modifier.padding(top = dimens.space32))
        if (state.inviterName != null) InviterChip(state.inviterName, state.inviterColor ?: colors.avatarOthers.first())
        Illustration(Modifier.padding(vertical = dimens.space24))
        Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
            Text(
                stringResource(R.string.welcome_title_line1),
                style = DurtTheme.type.welcomeTitle,
                color = colors.darkText,
            )
            Text(
                stringResource(R.string.welcome_title_line2),
                style = DurtTheme.type.welcomeTitle,
                color = colors.accent,
            )
        }
        Text(
            text =
                stringResource(
                    if (state.inviterName !=
                        null
                    ) {
                        R.string.welcome_invite_body
                    } else {
                        R.string.welcome_body
                    },
                ),
            style = DurtTheme.type.bodyLarge,
            color = colors.darkTextSecondary,
        )
    }
}

@Composable
private fun TermsText(
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val colors = DurtTheme.colors
    val terms = stringResource(R.string.welcome_terms_link)
    val privacy = stringResource(R.string.welcome_privacy_link)
    val sentence = stringResource(R.string.welcome_terms, terms, privacy)
    val linkStyle = TextLinkStyles(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline))
    val text =
        buildAnnotatedString {
            val termsAt = sentence.indexOf(terms)
            val privacyAt = sentence.indexOf(privacy)
            append(sentence.substring(0, termsAt))
            withLink(LinkAnnotation.Clickable("terms", linkStyle) { onTerms() }) { append(terms) }
            append(sentence.substring(termsAt + terms.length, privacyAt))
            withLink(LinkAnnotation.Clickable("privacy", linkStyle) { onPrivacy() }) { append(privacy) }
            append(sentence.substring(privacyAt + privacy.length))
        }
    Text(
        text = text,
        style = DurtTheme.type.caption,
        color = colors.textMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag(TestTags.WELCOME_TERMS),
    )
}

@Composable
private fun InviterChip(
    name: String,
    color: Color,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    val text = stringResource(R.string.welcome_invite_chip, name)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.darkSurface, shape)
                .border(dimens.borderCard, colors.darkBorder, shape)
                .padding(dimens.space10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Avatar(name, color, dimens.avatarNotification)
        Text(
            text =
                buildAnnotatedString {
                    val at = text.indexOf(name)
                    if (at < 0) {
                        append(text)
                    } else {
                        append(text.substring(0, at))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
                        append(text.substring(at + name.length))
                    }
                },
            style = DurtTheme.type.bodyLarge,
            color = colors.darkText,
        )
    }
}

/** The escalation from the design: a quiet poke, a louder one, the full screen one, then done. Decorative. */
@Composable
private fun Illustration(modifier: Modifier = Modifier) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val tilt = dimens.illustrationTilt
    Column(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(dimens.space10),
    ) {
        PokeChip(
            text = stringResource(R.string.welcome_demo_first),
            time = stringResource(R.string.welcome_demo_first_time),
            background = colors.darkSurface,
            textColor = colors.darkTextSecondary,
            dotColor = colors.textMuted,
            modifier = Modifier.rotate(-tilt / 2),
        )
        PokeChip(
            text = stringResource(R.string.welcome_demo_second),
            time = stringResource(R.string.welcome_demo_second_time),
            background = colors.warningPill,
            textColor = colors.ink,
            dotColor = colors.poke,
            bold = true,
            modifier = Modifier.align(Alignment.CenterHorizontally).rotate(tilt / 2),
        )
        IconChip(
            text = stringResource(R.string.welcome_demo_third),
            icon = DurtIcons.Bell,
            background = colors.poke,
            big = true,
            modifier = Modifier.align(Alignment.End).rotate(-tilt),
        )
        IconChip(
            text = stringResource(R.string.welcome_demo_done),
            icon = DurtIcons.Check,
            background = colors.success,
            big = false,
        )
    }
}

@Composable
private fun PokeChip(
    text: String,
    time: String,
    background: Color,
    textColor: Color,
    dotColor: Color,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    val dimens = DurtTheme.dimens
    Row(
        modifier =
            modifier
                .background(background, RoundedCornerShape(dimens.radiusL))
                .padding(horizontal = dimens.space16, vertical = dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Box(Modifier.size(dimens.statusDot).background(dotColor, CircleShape))
        Text(
            text,
            style = if (bold) DurtTheme.type.bodyLarge.copy(fontWeight = FontWeight.Bold) else DurtTheme.type.bodyLarge,
            color = textColor,
        )
        Text(time, style = DurtTheme.type.caption, color = textColor)
    }
}

@Composable
private fun IconChip(
    text: String,
    icon: ImageVector,
    background: Color,
    big: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Row(
        modifier =
            modifier
                .background(background, RoundedCornerShape(if (big) dimens.radiusXl else dimens.radiusM))
                .padding(horizontal = dimens.space20, vertical = if (big) dimens.space20 else dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.darkText,
            modifier = Modifier.size(if (big) dimens.iconLarge else dimens.icon),
        )
        Text(text, style = if (big) DurtTheme.type.h2 else DurtTheme.type.bodyStrong, color = colors.darkText)
    }
}

@PreviewScreens
@Composable
private fun WelcomeScreenPreview() {
    DurtTheme { WelcomeScreen(WelcomeUiState(), onStart = {}, onTerms = {}, onPrivacy = {}) }
}

@PreviewScreens
@Composable
private fun WelcomeInvitedScreenPreview() {
    DurtTheme { WelcomeScreen(WelcomeUiState(inviterName = "Selin"), onStart = {}, onTerms = {}, onPrivacy = {}) }
}
