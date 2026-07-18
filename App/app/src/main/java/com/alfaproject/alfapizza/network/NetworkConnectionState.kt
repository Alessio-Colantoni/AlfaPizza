package com.alfaproject.alfapizza.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.alfaproject.alfapizza.isNetworkConnected

@Composable
fun rememberNetworkConnectionState(): State<Boolean> {
    val applicationContext = LocalContext.current.applicationContext
    val connected = remember(applicationContext) {
        mutableStateOf(isNetworkConnected(applicationContext))
    }

    DisposableEffect(applicationContext) {
        val manager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val mainHandler = Handler(Looper.getMainLooper())

        fun refresh() {
            mainHandler.post {
                connected.value = isNetworkConnected(applicationContext)
            }
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()

            override fun onLost(network: Network) = refresh()

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                refresh()
            }
        }

        val registered = runCatching {
            manager.registerDefaultNetworkCallback(callback)
        }.isSuccess
        refresh()

        onDispose {
            if (registered) runCatching { manager.unregisterNetworkCallback(callback) }
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    return connected
}
