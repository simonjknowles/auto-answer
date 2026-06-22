package com.simon.autoanswer.debug

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkAddress {

    fun lanIpAddress(): String? = try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && it.hostAddress != null }
            .firstOrNull()
            ?.hostAddress
    } catch (e: Exception) {
        null
    }
}
