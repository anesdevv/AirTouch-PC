package com.example

import com.example.network.PcHost
import com.example.network.QrPairingParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrPairingParserTest {

    @Test
    fun parsesAirtouchUriPayloadWithAlternates() {
        val host = QrPairingParser.parse("airtouch://192.168.1.42:54546?u=54545&n=DESKTOP-WORK&a=192.168.1.43,10.0.0.8")
        assertEquals(
            PcHost(
                name = "DESKTOP-WORK",
                ipAddress = "192.168.1.42",
                tcpPort = 54546,
                udpPort = 54545,
                alternateIps = listOf("192.168.1.43", "10.0.0.8"),
            ),
            host,
        )
        assertEquals(listOf("192.168.1.42", "192.168.1.43", "10.0.0.8"), host?.candidateIps)
    }

    @Test
    fun parsesAtpcCompactUriPayload() {
        val host = QrPairingParser.parse("ATPC://10.0.0.15:54546/54545/LivingRoomPC")
        assertEquals("LivingRoomPC", host?.name)
        assertEquals("10.0.0.15", host?.ipAddress)
        assertEquals(54546, host?.tcpPort)
        assertEquals(54545, host?.udpPort)
    }

    @Test
    fun parsesJsonAndPlainIpPayloads() {
        val fromJson = QrPairingParser.parse("""{"ip":"192.168.0.105","tcp":55000,"udp":55001,"name":"Studio"}""")
        assertEquals("Studio", fromJson?.name)
        assertEquals("192.168.0.105", fromJson?.ipAddress)
        assertEquals(55000, fromJson?.tcpPort)
        assertEquals(55001, fromJson?.udpPort)

        val fromPlain = QrPairingParser.parse("192.168.1.88:54546")
        assertEquals("192.168.1.88", fromPlain?.ipAddress)
        assertEquals(54546, fromPlain?.tcpPort)
    }

    @Test
    fun rejectsUnrelatedQrPayloads() {
        assertNull(QrPairingParser.parse("https://example.com/hello"))
        assertNull(QrPairingParser.parse("random text"))
        assertNull(QrPairingParser.parse(""))
    }
}
