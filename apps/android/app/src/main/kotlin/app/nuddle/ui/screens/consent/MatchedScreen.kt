package app.nuddle.ui.screens.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.Avatar
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.theme.DurtTheme

/** 0g: dark celebration after İzin ver. Başla goes on to setup, or home when nothing is missing. */
@Composable
fun MatchedScreen(
    inviterName: String,
    myName: String,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.darkBackground,
        modifier = modifier.testTag(TestTags.MATCHED),
        horizontalAlignment = Alignment.CenterHorizontally,
        bottom = {
            DurtButton(
                text = stringResource(R.string.matched_start),
                onClick = onStart,
                kind = DurtButtonKind.Accent,
                modifier = Modifier.testTag(TestTags.MATCHED_START),
            )
        },
    ) {
        Row(Modifier.padding(top = dimens.space48 * 2).clearAndSetSemantics { }) {
            Avatar(inviterName, colors.avatarOthers.first(), dimens.avatarHero)
            Avatar(myName, colors.surface, dimens.avatarHero, Modifier.offset(x = -dimens.space12), letterColor = colors.ink)
        }
        Column(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.matched_title_line1, inviterName), style = DurtTheme.type.h1, color = colors.darkText)
            Text(stringResource(R.string.matched_title_line2), style = DurtTheme.type.h1, color = colors.accent)
        }
        Text(
            stringResource(R.string.matched_body, inviterName),
            style = DurtTheme.type.body,
            color = colors.darkTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = dimens.space16),
        )
    }
}

@PreviewScreens
@Composable
private fun MatchedScreenPreview() {
    DurtTheme { MatchedScreen(inviterName = "Sibel", myName = "Eren", onStart = {}) }
}
