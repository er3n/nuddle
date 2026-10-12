package app.nuddle.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line icons used by the components (24x24, 2 dp stroke, drawn black and tinted by `Icon`); Play is filled.
 * Kept here instead of material-icons-extended, which is large and no longer part of the Compose BOM defaults.
 */
object DurtIcons {
    val Back by lazy { stroke("Back", "M15 18l-6-6 6-6") }
    val ChevronRight by lazy { stroke("ChevronRight", "M9 18l6-6-6-6") }
    val ChevronDown by lazy { stroke("ChevronDown", "M6 9l6 6 6-6") }
    val Bell by lazy {
        stroke(
            "Bell",
            "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15L6 16z",
            "M10 20.5a2 2 0 0 0 4 0",
        )
    }
    val Mic by lazy {
        stroke(
            "Mic",
            "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z",
            "M5 11a7 7 0 0 0 14 0",
            "M12 18v3",
        )
    }
    val Send by lazy { stroke("Send", "M12 19V5", "M5 12l7-7 7 7") }
    val Check by lazy { stroke("Check", "M5 12.5l4.5 4.5L19 7.5") }
    val Warning by lazy {
        stroke("Warning", "M12 4L2.5 20h19L12 4z", "M12 10v4", "M12 17.5v0.5")
    }
    val Shield by lazy {
        stroke("Shield", "M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6l7-3z", "M9 12l2 2 4-4")
    }
    val Camera by lazy {
        stroke(
            "Camera",
            "M4 8h3l2-3h6l2 3h3v11H4V8z",
            "M12 16.5a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
        )
    }
    val Plus by lazy { stroke("Plus", "M12 5v14", "M5 12h14") }
    val Play by lazy { fill("Play", "M7 5v14l12-7z") }
    val Close by lazy { stroke("Close", "M6 6l12 12", "M18 6L6 18") }
    val PersonPlus by lazy {
        stroke("PersonPlus", "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M3 20a6 6 0 0 1 12 0", "M19 8v6", "M16 11h6")
    }

    val Phone by lazy {
        stroke(
            "Phone",
            "M21 16.5v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 1.1 3.7 2 2 0 0 1 3.1 1.5h3" +
                "a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L7 9.4a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7" +
                "a2 2 0 0 1 1.8 2.1z",
        )
    }
    val Message by lazy { stroke("Message", "M7.9 20A9 9 0 1 0 4 16.1L2 22l5.9-2z") }

    private fun fill(
        name: String,
        path: String,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()

    private fun stroke(
        name: String,
        vararg paths: String,
    ): ImageVector {
        val builder =
            ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
        paths.forEach { data ->
            builder.addPath(
                pathData = addPathNodes(data),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
