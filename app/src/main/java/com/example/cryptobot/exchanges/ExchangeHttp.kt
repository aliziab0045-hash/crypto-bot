package com.example.cryptobot.exchanges

import okhttp3.OkHttpClient
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.UnknownHostException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private class FallbackDns : okhttp3.Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        try {
            return okhttp3.Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            for (server in listOf("8.8.8.8", "1.1.1.1", "8.8.4.4")) {
                try {
                    val result = udpDnsLookup(hostname, server)
                    if (result.isNotEmpty()) return result
                } catch (ignored: Exception) {
                }
            }
            throw e
        }
    }

    private fun udpDnsLookup(hostname: String, dnsServer: String): List<InetAddress> {
        val socket = DatagramSocket()
        socket.soTimeout = 4000
        try {
            val query = buildDnsQuery(hostname)
            val serverAddress = InetAddress.getByName(dnsServer)
            val packet = DatagramPacket(query, query.size, serverAddress, 53)
            socket.send(packet)

            val buffer = ByteArray(512)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            return parseDnsResponse(buffer)
        } finally {
            socket.close()
        }
    }

    private fun buildDnsQuery(hostname: String): ByteArray {
        val id = (0..0xFFFF).random()
        val header = byteArrayOf(
            (id shr 8).toByte(), id.toByte(),
            0x01, 0x00,
            0x00, 0x01,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x00
        )
        val question = mutableListOf<Byte>()
        hostname.split(".").forEach { label ->
            question.add(label.length.toByte())
            question.addAll(label.toByteArray().toList())
        }
        question.add(0)
        question.add(0x00); question.add(0x01)
        question.add(0x00); question.add(0x01)
        return header + question.toByteArray()
    }

    private fun parseDnsResponse(buffer: ByteArray): List<InetAddress> {
        val ancount = ((buffer[6].toInt() and 0xFF) shl 8) or (buffer[7].toInt() and 0xFF)
        var pos = 12
        while (buffer[pos].toInt() != 0) {
            pos += (buffer[pos].toInt() and 0xFF) + 1
        }
        pos += 5

        val addresses = mutableListOf<InetAddress>()
        repeat(ancount) {
            if ((buffer[pos].toInt() and 0xC0) == 0xC0) {
                pos += 2
            } else {
                while (buffer[pos].toInt() != 0) {
                    pos += (buffer[pos].toInt() and 0xFF) + 1
                }
                pos += 1
            }
            val type = ((buffer[pos].toInt() and 0xFF) shl 8) or (buffer[pos + 1].toInt() and 0xFF)
            pos += 4
            pos += 4
            val rdLength = ((buffer[pos].toInt() and 0xFF) shl 8) or (buffer[pos + 1].toInt() and 0xFF)
            pos += 2
            if (type == 1 && rdLength == 4) {
                val ip = "${buffer[pos].toInt() and 0xFF}.${buffer[pos + 1].toInt() and 0xFF}." +
                    "${buffer[pos + 2].toInt() and 0xFF}.${buffer[pos + 3].toInt() and 0xFF}"
                addresses.add(InetAddress.getByName(ip))
            }
            pos += rdLength
        }
        return addresses
    }
}

object ExchangeHttp {
    val client = OkHttpClient.Builder()
        .dns(FallbackDns())
        .build()

    fun hmacSha256(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
