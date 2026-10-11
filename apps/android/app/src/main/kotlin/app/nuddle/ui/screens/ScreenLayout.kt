package app.nuddle.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import app.nuddle.ui.theme.DurtTheme

/**
 * Every full screen in the sign-in flow: content at the top, actions pinned to the bottom, and the whole thing
 * scrolls when it does not fit (font scale 200%, small phones, keyboard open). Horizontal padding is 20 dp, 16 dp on
 * phones 360 dp wide or less (tokens.json, space).
 */
@Composable
fun ScreenLayout(
    background: Color,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    bottom: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = DurtTheme.dimens
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        val padding =
            horizontalPadding
                ?: if (maxWidth <=
                    dimens.smallPhoneWidth
                ) {
                    dimens.screenPaddingHorizontalSmallPhone
                } else {
                    dimens.screenPaddingHorizontal
                }
        val minHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .padding(
                            start = padding,
                            end = padding,
                            top = dimens.screenPaddingTop,
                            bottom = dimens.screenPaddingBottom,
                        ),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(dimens.space16),
                    horizontalAlignment = horizontalAlignment,
                    content = content,
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = dimens.space24),
                    verticalArrangement = Arrangement.spacedBy(dimens.space12),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = bottom,
                )
            }
        }
    }
}
