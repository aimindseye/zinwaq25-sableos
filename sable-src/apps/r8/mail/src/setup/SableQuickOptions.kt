// SableOS: account setup is one screen. After the server checks, the Display options and Sync options steps
// fill themselves in and move on, so a person types an address and a password and nothing else.
package app.k9mail.feature.account.setup.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import app.k9mail.feature.account.setup.ui.options.display.DisplayOptionsContract
import app.k9mail.feature.account.setup.ui.options.sync.SyncOptionsContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.thunderbird.core.ui.contract.mvi.observe

private const val OWNER_NAME_WAIT_MILLIS = 1_000L

/** The Display options step without its form: the account is named by its address, the person by the owner's name. */
@Composable
internal fun SableAutoDisplayOptions(
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: DisplayOptionsContract.ViewModel,
    modifier: Modifier = Modifier,
) {
    val (state, dispatch) =
        viewModel.observe { effect ->
            when (effect) {
                DisplayOptionsContract.Effect.NavigateBack -> onBack()
                DisplayOptionsContract.Effect.NavigateNext -> onNext()
            }
        }

    LaunchedEffect(key1 = Unit) {
        dispatch(DisplayOptionsContract.Event.LoadAccountState)
        // The owner's name is read in the background; use it when it arrives, else the part of the address before @.
        val owner =
            withTimeoutOrNull(OWNER_NAME_WAIT_MILLIS) {
                snapshotFlow { state.value.displayName.value }.first { it.isNotEmpty() }
            }
        if (owner == null) {
            val address = state.value.accountName.value
            val fallback = address.substringBefore('@').ifEmpty { address }
            dispatch(DisplayOptionsContract.Event.OnDisplayNameChanged(fallback))
        }
        dispatch(DisplayOptionsContract.Event.OnNextClicked)
    }

    Box(modifier.fillMaxSize())
}

/** The Sync options step without its form: the default check frequency, message count and notifications. */
@Composable
internal fun SableAutoSyncOptions(
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: SyncOptionsContract.ViewModel,
    modifier: Modifier = Modifier,
) {
    val (_, dispatch) =
        viewModel.observe { effect ->
            when (effect) {
                SyncOptionsContract.Effect.NavigateBack -> onBack()
                SyncOptionsContract.Effect.NavigateNext -> onNext()
            }
        }

    LaunchedEffect(key1 = Unit) {
        dispatch(SyncOptionsContract.Event.LoadAccountState)
        dispatch(SyncOptionsContract.Event.OnNextClicked)
    }

    Box(modifier.fillMaxSize())
}
