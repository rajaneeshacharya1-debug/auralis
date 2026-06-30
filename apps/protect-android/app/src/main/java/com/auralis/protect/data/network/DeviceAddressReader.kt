package com.auralis.protect.data.network

import java.net.Inet4Address
import java.net.NetworkInterface

object DeviceAddressReader {
    fun readLocalIpv4Addresses(): List<String> {
        return try {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .filter { networkInterface ->
                    networkInterface.isUp && !networkInterface.isLoopback
                }
                .flatMap { networkInterface ->
                    networkInterface.inetAddresses.toList()
                }
                .filterIsInstance<Inet4Address>()
                .filter { address ->
                    !address.isLoopbackAddress && !address.hostAddress.startsWith("127.")
                }
                .map { address ->
                    address.hostAddress
                }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun primaryAddress(): String {
        return readLocalIpv4Addresses().firstOrNull() ?: "unavailable"
    }
}
