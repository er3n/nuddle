package app.nuddle.ui.screens.home

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.PillStatus
import app.nuddle.ui.components.TaskRow
import app.nuddle.ui.components.TaskUi
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.theme.avatarFor
import app.nuddle.ui.uppercaseForUi

/** 4i: "Senin yazdıkların" and a TaskRow per task; [fresh] is the one whose undo toast is on screen ("Yeni"). */
@Composable
internal fun ColumnScope.SentSection(
    sent: List<SentUi>,
    fresh: String?,
    onOpen: (String) -> Unit,
) {
    val colors = DurtTheme.colors
    Text(
        text = stringResource(R.string.home_sent).uppercaseForUi(),
        style = DurtTheme.type.overline,
        color = colors.textTertiary,
        modifier = Modifier.semantics { heading() },
    )
    sent.forEach { task ->
        val isFresh = task.taskId == fresh
        val parts =
            listOfNotNull(task.detail, task.deadline.inlineText(), if (task.photo) stringResource(R.string.sent_photo) else null)
        TaskRow(
            task =
                TaskUi(
                    title = task.title,
                    subtitle = parts.reduce { line, part -> stringResource(R.string.separator_dot, line, part) },
                    status = if (isFresh) PillStatus.New else PillStatus.Scheduled,
                    personName = task.toName,
                    personColor = colors.avatarFor(task.toId),
                ),
            onClick = { onOpen(task.taskId) },
            highlighted = isFresh,
            modifier = Modifier.testTag(TestTags.HOME_SENT),
        )
    }
}

@PreviewScreens
@Composable
private fun HomeScreenQuickSentPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Selin",
                    connected = true,
                    sent =
                        listOf(
                            SentUi("task-1", "Temizlikçi için para çek", "1.000 TL", DeadlineText.Tomorrow("09:00"), true, "eren", "Eren"),
                        ),
                    undo =
                        UndoUi(
                            taskId = "task-1",
                            rawText = "eren yarın 9dan önce temizlikçi için 1000 çeksin",
                            title = "Temizlikçi için para çek",
                            detail = "1.000 TL",
                            deadline = DeadlineText.Tomorrow("09:00"),
                            photo = true,
                            to = "Eren",
                        ),
                ),
            onBell = {},
            onProfile = {},
            onInvite = {},
            onComposeTextChange = {},
            onComposeSend = {},
            onVoiceStart = {},
            onVoiceEnd = {},
        )
    }
}
