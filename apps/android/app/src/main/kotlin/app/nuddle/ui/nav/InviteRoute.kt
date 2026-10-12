package app.nuddle.ui.nav

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nuddle.R
import app.nuddle.ui.screens.invite.InviteCallbacks
import app.nuddle.ui.screens.invite.InviteScreen
import app.nuddle.ui.screens.invite.InviteViewModel

/**
 * Picks one phone number from contacts. The picker grants read access to that one row only, so no contacts
 * permission is needed (docs/ux/platform.md, Permissions).
 */
private class PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(
        context: Context,
        input: Unit,
    ): Intent = Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Uri? = intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}

/** Name and number of the picked row, or null when it cannot be read. */
private fun Context.readPhone(uri: Uri): Pair<String, String>? =
    contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        (cursor.getString(0) ?: "") to (cursor.getString(1) ?: return@use null)
    }

/** The system share sheet with the invite link (1f: "Daveti gönder" opens it). */
internal fun Activity.shareInvite(link: String) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.invite_share_text, link))
    startActivity(Intent.createChooser(send, null))
}

/** The `invite` route (1e, 1f). After the share sheet opens, home shows the pending invite (1g). */
@Composable
fun InviteRoute(
    onBack: () -> Unit,
    onShare: () -> Unit,
    viewModel: InviteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val shared by rememberUpdatedState(onShare)
    val picker =
        rememberLauncherForActivityResult(PickPhoneNumber()) { uri ->
            val picked = uri?.let { activity?.readPhone(it) } ?: return@rememberLauncherForActivityResult
            viewModel.onContactPicked(picked.first, picked.second)
        }
    LaunchedEffect(viewModel) {
        viewModel.share.collect { link ->
            activity?.shareInvite(link)
            shared()
        }
    }
    InviteScreen(
        state = state,
        callbacks =
            InviteCallbacks(
                onBack = onBack,
                onPickContact = { picker.launch(Unit) },
                onTypeNumber = viewModel::onTypeNumber,
                onTypedName = viewModel::onTypedName,
                onTypedNumber = viewModel::onTypedNumber,
                onRelationship = viewModel::onRelationship,
                onSend = viewModel::onSend,
            ),
    )
}
