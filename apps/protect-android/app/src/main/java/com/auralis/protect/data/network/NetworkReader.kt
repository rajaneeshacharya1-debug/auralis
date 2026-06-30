package com.auralis.protect.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

data class NetworkStatus(
    val value: String,
    val detail: String,
    val connected: Boolean
)

object NetworkReader {
    fun read(context: Context): NetworkStatus {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetwork
            ?: return NetworkStatus(
                value = "Offline",
                detail = "no active network",
                connected = false
            )

        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return NetworkStatus(
                value = "Unknown",
                detail = "network unavailable",
                connected = false
            )

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                NetworkStatus(
                    value = "Wi-Fi",
                    detail = "connected",
                    connected = true
                )
            }

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                NetworkStatus(
                    value = "Mobile",
                    detail = "cellular data",
                    connected = true
                )
            }

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                NetworkStatus(
                    value = "Ethernet",
                    detail = "connected",
                    connected = true
                )
            }

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
                NetworkStatus(
                    value = "VPN",
                    detail = "secure tunnel",
                    connected = true
                )
            }

            else -> {
                NetworkStatus(
                    value = "Connected",
                    detail = "other network",
                    connected = true
                )
            }
        }
    }
}
