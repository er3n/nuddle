package app.nuddle.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.nuddle.R
import app.nuddle.api.model.EdgeType
import app.nuddle.api.model.SyncSentInvite
import app.nuddle.data.db.InviteEntity
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.Avatar
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.DurtButtonSize
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.uppercaseForUi
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** 1g: the invite I sent that is still waiting for the other person. */
@Immutable
data class PendingInviteUi(
    val inviteId: String,
    val code: String,
    val name: String,
    val relationship: EdgeType,
    val sharedAt: DeadlineText,
)

/** The newest sent invite still open. A decline looks open to the inviter, so it stays here too. */
fun pendingInvite(
    invites: List<InviteEntity>,
    now: Instant,
    timeZone: TimeZone,
): PendingInviteUi? =
    invites
        .filter { it.sent && it.status == SyncSentInvite.Status.OPEN.value }
        .maxByOrNull { it.createdAt }
        ?.let { invite ->
            PendingInviteUi(
                inviteId = invite.id,
                code = invite.code,
                name = invite.name,
                relationship = EdgeType.decode(invite.relationship) ?: EdgeType.OTHER,
                sharedAt = deadlineText(Instant.fromEpochMilliseconds(invite.createdAt), now, timeZone),
            )
        }

/** "Eşin", "Annen / Baban" ...; null for "Diğer", which has no "X olarak". */
private val EdgeType.asYours: Int?
    get() =
        when (this) {
            EdgeType.SPOUSE -> R.string.relationship_as_spouse
            EdgeType.PARENT -> R.string.relationship_as_parent
            EdgeType.CHILD -> R.string.relationship_as_child
            EdgeType.SIBLING -> R.string.relationship_as_sibling
            EdgeType.ROOMMATE -> R.string.relationship_as_roommate
            EdgeType.OTHER -> null
        }

@Composable
internal fun PendingInviteCard(
    myName: String,
    invite: PendingInviteUi,
    onReshare: () -> Unit,
    onRevoke: () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    val shared = stringResource(R.string.pending_invite_shared, invite.sharedAt.text())
    val subline =
        invite.relationship.asYours?.let {
            stringResource(R.string.separator_dot, shared, stringResource(R.string.pending_invite_as, stringResource(it)))
        }
            ?: shared
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(dimens.borderCard, colors.border, shape)
                .padding(dimens.space20)
                .testTag(TestTags.HOME_PENDING_INVITE),
        verticalArrangement = Arrangement.spacedBy(dimens.space14),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.space12), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clearAndSetSemantics { }) {
                Avatar(myName, colors.avatarSelf, dimens.avatarTopBar)
                DashedInitial(invite.name, Modifier.offset(x = -dimens.space4))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pending_invite_title, invite.name), style = DurtTheme.type.bodyStrong, color = colors.ink)
                Text(subline, style = DurtTheme.type.caption, color = colors.textSecondary)
            }
        }
        Text(stringResource(R.string.pending_invite_body, invite.name), style = DurtTheme.type.bodySmall, color = colors.textBody)
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.space12)) {
            DurtButton(
                text = stringResource(R.string.pending_invite_reshare),
                onClick = onReshare,
                kind = DurtButtonKind.Secondary,
                size = DurtButtonSize.Small,
                modifier = Modifier.weight(1f).testTag(TestTags.HOME_INVITE_RESHARE),
            )
            DurtButton(
                text = stringResource(R.string.pending_invite_revoke),
                onClick = onRevoke,
                kind = DurtButtonKind.DangerGhost,
                size = DurtButtonSize.Small,
                modifier = Modifier.weight(1f).testTag(TestTags.HOME_INVITE_REVOKE),
            )
        }
    }
}

/** The invitee's initial in a dashed circle: not on dürt yet. */
@Composable
private fun DashedInitial(
    name: String,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Box(
        modifier =
            modifier
                .size(dimens.avatarTopBar)
                .background(colors.highlightSoft, CircleShape)
                .drawBehind {
                    drawCircle(
                        color = colors.poke,
                        radius = size.minDimension / 2 - dimens.borderStrong.toPx(),
                        center = Offset(size.width / 2, size.height / 2),
                        style =
                            Stroke(
                                width = dimens.borderStrong.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)),
                            ),
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1).uppercaseForUi(), style = DurtTheme.type.bodyStrong, color = colors.poke)
    }
}
