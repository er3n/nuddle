package app.nuddle.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the phone can reach the internet right now (6a). */
interface Connectivity {
    val online: StateFlow<Boolean>
}

/**
 * Whether there is a default network that offers internet. Not whether Android validated it: some networks never pass
 * the check, and a phone that thinks it is offline forever would never send anything. A dead Wi-Fi shows as a write
 * that gets no answer (6b) instead.
 */
@Singleton
class AndroidConnectivity
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : Connectivity {
        private val manager = context.getSystemService(ConnectivityManager::class.java)
        private val state = MutableStateFlow(manager.getNetworkCapabilities(manager.activeNetwork).hasInternet())
        override val online: StateFlow<Boolean> = state.asStateFlow()

        init {
            manager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        state.value = capabilities.hasInternet()
                    }

                    override fun onLost(network: Network) {
                        state.value = false
                    }
                },
            )
        }

        private fun NetworkCapabilities?.hasInternet(): Boolean = this != null && hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
