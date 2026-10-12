package app.nuddle.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import java.util.Locale

/**
 * Uppercase in the language of the strings on screen, not the device's (NUD-37): "Sıradaki" stays "SIRADAKİ" on a phone
 * set to English, because the Turkish strings are its default resources. User content (an avatar initial) follows the
 * same rule, since the app's language is the best guess for it.
 */
@Composable
fun String.uppercaseForUi(): String = uppercase(Locale.forLanguageTag(stringResource(R.string.ui_locale)))
