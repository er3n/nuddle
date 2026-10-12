package app.nuddle.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.nuddle.api.model.EdgeType
import app.nuddle.domain.ConnectedFor
import app.nuddle.ui.screens.person.PersonCallbacks
import app.nuddle.ui.screens.person.PersonScreen
import app.nuddle.ui.screens.person.PersonSheet
import app.nuddle.ui.screens.person.PersonUiState
import app.nuddle.ui.screens.person.previewPerson
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** 5b: the rows, the sheets and what is never shown (open-questions#4, #37). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PersonScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val did = mutableListOf<String>()

    private fun show(state: PersonUiState = previewPerson) {
        compose.setContent {
            DurtTheme {
                PersonScreen(
                    state,
                    PersonCallbacks(
                        onRelationship = { did += "relationship" },
                        onNotifyMe = { did += "notifyMe $it" },
                        onRemove = { did += "remove" },
                        onSelect = { did += "select ${it.value}" },
                        onConfirm = { did += "confirm" },
                    ),
                )
            }
        }
    }

    @Test
    fun `says since when without a percentage, and each row does its part`() {
        show()
        compose.onNodeWithText("Eşin · 3 aydır bağlısınız").assertExists()
        compose.onNodeWithText("24 görev").assertExists()
        compose.onNodeWithText("Zamanında biten").assertDoesNotExist()
        compose.onNodeWithTag(TestTags.PERSON_RELATIONSHIP).performClick()
        compose.onNodeWithTag(TestTags.PERSON_NOTIFY_ME).performClick()
        compose.onNodeWithTag(TestTags.PERSON_REMOVE).performScrollTo().performClick()
        assertEquals(listOf("relationship", "notifyMe false", "remove"), did)
    }

    @Test
    fun `a connection made today says so`() {
        show(previewPerson.copy(relationship = EdgeType.OTHER, since = ConnectedFor.Today))
        compose.onNodeWithText("Diğer · bugün bağlandınız").assertExists()
    }

    @Test
    fun `the relationship sheet offers every type and saves`() {
        show(previewPerson.copy(sheet = PersonSheet.Relationship(EdgeType.SPOUSE)))
        compose.onNodeWithTag(TestTags.personRelationship("roommate")).performClick()
        compose.onNodeWithTag(TestTags.SHEET_PRIMARY).performClick()
        assertEquals(listOf("select roommate", "confirm"), did)
    }
}
