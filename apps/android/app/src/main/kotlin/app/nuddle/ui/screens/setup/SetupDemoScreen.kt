package app.nuddle.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.theme.DurtTheme

/** 2c: setup starts by showing what a poke feels like. Bir dene rings the demo (2d) after 3 s. */
@Composable
fun SetupDemoScreen(
    state: SetupUiState.Intro,
    onTry: () -> Unit,
    onSkipDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.background,
        modifier = modifier.testTag(TestTags.SETUP),
        bottom = {
            DurtButton(
                text = stringResource(R.string.setup_try),
                onClick = onTry,
                kind = DurtButtonKind.Accent,
                leadingIcon = DurtIcons.Play,
                loading = state.starting,
                modifier = Modifier.testTag(TestTags.SETUP_TRY),
            )
            DurtButton(
                text = stringResource(R.string.setup_skip_demo),
                onClick = onSkipDemo,
                kind = DurtButtonKind.Ghost,
                enabled = !state.starting,
                modifier = Modifier.testTag(TestTags.SETUP_SKIP_DEMO),
            )
        },
    ) {
        SetupHeader(
            title = stringResource(R.string.setup_demo_title),
            body = stringResource(R.string.setup_demo_body_self),
            modifier = Modifier.padding(top = dimens.space48),
        )
        Box(Modifier.fillMaxWidth().padding(vertical = dimens.space32), contentAlignment = Alignment.Center) {
            DemoPhone()
        }
    }
}

/** "Bir kerelik kurulum" above the title, shared by 2c and 2e. */
@Composable
internal fun SetupHeader(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space8)) {
        Text(stringResource(R.string.setup_eyebrow), style = DurtTheme.type.captionStrong, color = colors.warning)
        Text(
            text = title,
            style = DurtTheme.type.h1,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) Text(body, style = DurtTheme.type.body, color = colors.textSecondary)
    }
}

/** The small phone with a poke on it. Decoration only: the text above says the same. */
@Composable
private fun DemoPhone() {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Column(
        modifier =
            Modifier
                .width(dimens.setupPhoneWidth)
                .heightIn(min = dimens.setupPhoneHeight)
                .background(colors.poke, RoundedCornerShape(dimens.setupPhoneRadius))
                .border(dimens.setupPhoneFrame, colors.ink, RoundedCornerShape(dimens.setupPhoneRadius))
                .padding(
                    start = dimens.space16 + dimens.setupPhoneFrame,
                    end = dimens.space16 + dimens.setupPhoneFrame,
                    top = dimens.space24 + dimens.setupPhoneFrame,
                    bottom = dimens.space16 + dimens.setupPhoneFrame,
                ).clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.space12),
            modifier = Modifier.padding(bottom = dimens.space12),
        ) { DemoPhonePoke() }
        Box(
            Modifier
                .fillMaxWidth()
                .height(dimens.setupPhoneButton)
                .background(colors.surface, RoundedCornerShape(dimens.setupPhoneButtonRadius)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.poke_done),
                style = DurtTheme.type.captionStrong.copy(fontWeight = FontWeight.Bold),
                color = colors.pokeDeep,
            )
        }
    }
}

@Composable
private fun ColumnScope.DemoPhonePoke() {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Box(
        Modifier.size(dimens.setupPhoneRing).border(dimens.borderFocused, colors.pokeRing, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(dimens.setupPhoneBell).background(colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(DurtIcons.Bell, contentDescription = null, tint = colors.poke, modifier = Modifier.size(dimens.iconLarge))
        }
    }
    Text(stringResource(R.string.setup_demo_task), style = DurtTheme.type.h2, color = colors.surface)
    Text(
        text = stringResource(R.string.setup_demo_phone_caption),
        style = DurtTheme.type.caption,
        color = colors.onPokeSoft,
        textAlign = TextAlign.Center,
    )
}

@PreviewScreens
@Composable
private fun SetupDemoScreenPreview() {
    DurtTheme { SetupDemoScreen(SetupUiState.Intro(), onTry = {}, onSkipDemo = {}) }
}

@PreviewScreens
@Composable
private fun SetupDemoScreenStartingPreview() {
    DurtTheme { SetupDemoScreen(SetupUiState.Intro(starting = true), onTry = {}, onSkipDemo = {}) }
}
