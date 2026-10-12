package app.nuddle.ui.screens.poke

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.DurtButtonSize
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.theme.DurtTheme

/**
 * The demo poke of setup (2d): the full screen poke labeled "DENEME", with one answer that moves on to the settings.
 * [settingsLeft] is the "2" of the last line, which is hidden when nothing is left.
 */
@Composable
fun PokeDemoScreen(
    settingsLeft: Int,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.poke,
        modifier = modifier.testTag(TestTags.SETUP_DEMO),
        horizontalPadding = dimens.space24,
        horizontalAlignment = Alignment.CenterHorizontally,
        bottom = {
            DurtButton(
                text = stringResource(R.string.setup_demo_ok),
                onClick = onContinue,
                kind = DurtButtonKind.OnPoke,
                size = DurtButtonSize.Poke,
                modifier = Modifier.testTag(TestTags.SETUP_DEMO_OK),
            )
            if (settingsLeft > 0) {
                Text(
                    text = pluralStringResource(R.plurals.setup_demo_settings_left, settingsLeft, settingsLeft),
                    style = DurtTheme.type.caption,
                    color = colors.onPokeSoft,
                    textAlign = TextAlign.Center,
                )
            }
        },
    ) {
        Text(
            text = stringResource(R.string.setup_demo_badge),
            style = DurtTheme.type.captionStrong,
            color = colors.pokeDeep,
            modifier =
                Modifier
                    .padding(top = dimens.space32)
                    .background(colors.surface, RoundedCornerShape(dimens.radiusPill))
                    .padding(horizontal = dimens.space12, vertical = dimens.space6),
        )
        PokeRings(Modifier.padding(top = dimens.space12))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dimens.space10)) {
            Text(
                text = stringResource(R.string.setup_demo_task),
                style = DurtTheme.type.displayPoke,
                color = colors.surface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.setup_demo_from_self),
                style = DurtTheme.type.bodyLarge,
                color = colors.onPokeSoft,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = stringResource(R.string.setup_demo_message),
            style = DurtTheme.type.body,
            color = colors.surface,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.pokeDeep, RoundedCornerShape(dimens.radiusM))
                    .padding(horizontal = dimens.space16, vertical = dimens.space14),
        )
    }
}

@PreviewScreens
@Composable
private fun PokeDemoScreenPreview() {
    DurtTheme { PokeDemoScreen(settingsLeft = 2, onContinue = {}) }
}
