package com.dimadesu.djiremote.dji

import org.junit.Test
import org.junit.Assert.*

class DjiPayloadsTest {
    @Test
    fun pairPayloadRoundtrip() {
        val p = DjiPairMessagePayload("mbln")
        val enc = p.encode()
        val msg = DjiMessage(target = 0x0702, id = 0x8092, type = 0x450740, payload = enc)
        val out = DjiMessage.fromBytes(msg.encode())
        assertArrayEquals(enc, out.payload)
    }

    @Test
    fun setupWifiRoundtrip() {
        val p = DjiSetupWifiMessagePayload("ssid", "pass")
        val enc = p.encode()
        val msg = DjiMessage(target = 0x0702, id = 0x8C19, type = 0x470740, payload = enc)
        val out = DjiMessage.fromBytes(msg.encode())
        assertArrayEquals(enc, out.payload)
    }

    @Test
    fun startStreamingRoundtrip() {
        val p = DjiStartStreamingMessagePayload("rtmp://example/stream", SettingsDjiDeviceResolution.r1080p, 30, 4000, SettingsDjiDeviceModel.OSMO_ACTION_4)
        val enc = p.encode()
        val msg = DjiMessage(target = 0x0802, id = 0x8C2C, type = 0x780840, payload = enc)
        val out = DjiMessage.fromBytes(msg.encode())
        assertArrayEquals(enc, out.payload)
    }

    @Test
    fun testBitrateEncoding() {
        // Test 3Mbps (3000 kbps)
        val p3 = DjiStartStreamingMessagePayload("rtmp://x", SettingsDjiDeviceResolution.r1080p, 30, 3000, SettingsDjiDeviceModel.OSMO_ACTION_4)
        val enc3 = p3.encode()
        // Bitrate is at offset 5 and 6 (UInt16Le)
        // Payload: payload1(1) + byte1(1) + payload2(1) + resByte(1) + bitrate(2) ...
        // payload1 is byteArrayOf(0x00) -> index 0
        // byte1 -> index 1
        // payload2 is byteArrayOf(0x00) -> index 2
        // resolutionByte -> index 3
        // bitrateKbps (UInt16Le) -> index 4, 5
        assertEquals(0xB8.toByte(), enc3[4])
        assertEquals(0x0B.toByte(), enc3[5])

        // Test 5Mbps (5000 kbps)
        val p5 = DjiStartStreamingMessagePayload("rtmp://x", SettingsDjiDeviceResolution.r1080p, 30, 5000, SettingsDjiDeviceModel.OSMO_ACTION_4)
        val enc5 = p5.encode()
        assertEquals(0x88.toByte(), enc5[4])
        assertEquals(0x13.toByte(), enc5[5])

        // Test Pocket 4 encoding (uses Mbps instead of kbps in that specific field)
        val pP4 = DjiStartStreamingMessagePayload("rtmp://x", SettingsDjiDeviceResolution.r1080p, 30, 5000, SettingsDjiDeviceModel.OSMO_POCKET_4)
        val encP4 = pP4.encode()
        // Pocket 4 payload: 0x01, 0x01, bitrateMbps(UInt16Le), ...
        // bitrateMbps = 5000 / 1000 = 5
        assertEquals(0x05.toByte(), encP4[2])
        assertEquals(0x00.toByte(), encP4[3])
    }

    @Test
    fun pairMessageFormat() {
        val p = DjiPairMessagePayload("mbln")
        val enc = p.encode()
        val msg = DjiMessage(target = 0x0702, id = 0x8092, type = 0x450740, payload = enc)
        val bytes = msg.encode()

        // Log the message for debugging
        println("Pair message bytes (${bytes.size}): ${bytes.joinToString(" ") { "%02X".format(it) }}")

        // Verify message starts with 0x55 (DJI header)
        assertEquals(0x55.toByte(), bytes[0])

        // Verify length byte
        assertEquals(51, bytes[1].toInt() and 0xFF)

        // Verify we can decode our own message
        val decoded = DjiMessage.fromBytes(bytes)
        assertEquals(0x0702, decoded.target)
        assertEquals(0x8092, decoded.id)
        assertEquals(0x450740, decoded.type)
        assertArrayEquals(enc, decoded.payload)
    }
}
