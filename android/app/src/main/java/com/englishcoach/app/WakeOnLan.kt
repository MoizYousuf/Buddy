package com.englishcoach.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Wake-on-LAN: the "magic packet" (6 × 0xFF, then the MAC 16 times) sent by UDP to ports 9 and 7.
 * At home it goes to the Wi-Fi broadcast so the sleeping Mac's network card hears it; away, it is also sent to the
 * Mac's Tailscale address (that only helps if the Mac is in light sleep and its network is still up).
 */
object WakeOnLan {
    suspend fun send(info: JSONObject): Int = withContext(Dispatchers.IO) {
        val targets = info.optJSONArray("targets") ?: return@withContext 0
        val hosts = mutableSetOf("255.255.255.255")
        info.optString("tailscale").takeIf { it.isNotBlank() }?.let { hosts += it }
        val macs = mutableListOf<ByteArray>()
        for (i in 0 until targets.length()) {
            val t = targets.getJSONObject(i)
            parse(t.optString("mac"))?.let { macs += it }
            t.optString("broadcast").takeIf { it.isNotBlank() }?.let { hosts += it }
            t.optString("ip").takeIf { it.isNotBlank() }?.let { hosts += it }
        }
        var sent = 0
        DatagramSocket().use { sock ->
            sock.broadcast = true
            repeat(3) {
                for (mac in macs) {
                    val p = ByteArray(6) { 0xFF.toByte() } + (1..16).flatMap { mac.toList() }.toByteArray()
                    for (h in hosts) for (port in listOf(9, 7)) runCatching {
                        sock.send(DatagramPacket(p, p.size, InetAddress.getByName(h), port)); sent++
                    }
                }
                Thread.sleep(300)
            }
        }
        sent
    }

    private fun parse(mac: String): ByteArray? =
        mac.split(":").takeIf { it.size == 6 }?.map { it.toInt(16).toByte() }?.toByteArray()
}
